package com.srmlmobile.saveeditor;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import rikka.shizuku.Shizuku;

public final class ShizukuFileAccess {
 public static final String SAVE_DIR="/storage/emulated/0/Android/Data/com.playdigious.slimerancher/files/pt_saves";
 public static final String BACKUP_DIR="/storage/emulated/0/Android/Data/com.playdigious.slimerancher/files/Backup";
 private static final int MAX_BYTES=64*1024*1024;
 private static final String STAMP_PATTERN="dd.MM.yyyy HH-mm";
 private static Process sh(String c)throws Exception{
  java.lang.reflect.Method m=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);m.setAccessible(true);
  return (Process)m.invoke(null,new String[]{"sh","-c",c},null,null);
 }
 private static String q(String s){return "'"+s.replace("'","'\\''")+"'";}
 private static int run(String c)throws Exception{Process p=sh(c);try{return p.waitFor();}finally{p.destroy();}}
 private static boolean exists(String p)throws Exception{return run("test -f "+q(p))==0;}
 public static void ensureBackup()throws Exception{
  if(run("mkdir -p "+q(BACKUP_DIR)+" && test -d "+q(BACKUP_DIR)+" && test -w "+q(BACKUP_DIR))!=0)throw new IOException("Не удалось создать или открыть папку Backup");
 }
 public static List<String> listSaves()throws Exception{return listFiles(SAVE_DIR);}
 private static List<String> listFiles(String dir)throws Exception{
  Process p=sh("find "+q(dir)+" -maxdepth 1 -type f -print 2>/dev/null");ByteArrayOutputStream o=new ByteArrayOutputStream();
  try(InputStream i=p.getInputStream()){byte[]b=new byte[4096];int n;while((n=i.read(b))!=-1)o.write(b,0,n);}
  int x=p.waitFor();p.destroy();if(x!=0)throw new IOException("Не удалось прочитать папку: "+dir);
  List<String>r=new ArrayList<>();for(String s:new String(o.toByteArray(),"UTF-8").split("\\r?\\n"))if(!s.trim().isEmpty())r.add(s.trim());Collections.sort(r);return r;
 }
 public static byte[] read(String path)throws Exception{
  Process p=sh("cat -- "+q(path));ByteArrayOutputStream o=new ByteArrayOutputStream();
  try(InputStream i=p.getInputStream()){byte[]b=new byte[8192];int n;while((n=i.read(b))!=-1){if(o.size()+n>MAX_BYTES)throw new IOException("Файл больше 64 МБ");o.write(b,0,n);}}
  int x=p.waitFor();p.destroy();if(x!=0)throw new IOException("Не удалось прочитать файл: "+path);return o.toByteArray();
 }
 private static String uniqueBackupPath(String name)throws Exception{
  String base=BACKUP_DIR+"/"+name+" ("+new SimpleDateFormat(STAMP_PATTERN,Locale.ROOT).format(new Date())+")";
  if(!exists(base))return base;
  for(int n=2;n<10000;n++){String p=base+" ["+n+"]";if(!exists(p))return p;}
  throw new IOException("Не удалось подобрать уникальное имя резервной копии");
 }
 private static String backupVerified(String source,String originalName)throws Exception{
  ensureBackup();String dest=uniqueBackupPath(originalName);
  if(run("cp -p -- "+q(source)+" "+q(dest)+" && cmp -s -- "+q(source)+" "+q(dest))!=0)throw new IOException("Резервная копия не прошла побайтовую проверку");
  return dest;
 }
 public static final class BackupItem{
  public final String path,originalName,stamp;
  BackupItem(String p,String n,String s){path=p;originalName=n;stamp=s;}
 }
 public static final class RestoreResult{
  public final String restoredName,conflictBackupPath;
  RestoreResult(String n,String b){restoredName=n;conflictBackupPath=b;}
  public boolean hadConflict(){return conflictBackupPath!=null;}
 }
 private static final class BackupName{
  final String name,stamp;BackupName(String n,String s){name=n;stamp=s;}
 }
 private static BackupName parseBackupName(String filename){
  int close=filename.lastIndexOf(')');if(close<0)return null;
  int open=filename.lastIndexOf(" (",close);if(open<=0)return null;
  String stamp=filename.substring(open+2,close);if(stamp.length()!=16)return null;
  try{SimpleDateFormat f=new SimpleDateFormat(STAMP_PATTERN,Locale.ROOT);f.setLenient(false);f.parse(stamp);}catch(Exception e){return null;}
  String suffix=filename.substring(close+1);
  if(!suffix.isEmpty()&&!suffix.matches(" \\[([2-9]|[1-9][0-9]+)\\]"))return null;
  return new BackupName(filename.substring(0,open),stamp);
 }
 public static List<BackupItem> listBackups()throws Exception{
  ensureBackup();List<BackupItem>r=new ArrayList<>();
  for(String path:listFiles(BACKUP_DIR)){
   BackupName n=parseBackupName(path.substring(path.lastIndexOf('/')+1));
   if(n!=null)r.add(new BackupItem(path,n.name,n.stamp));
  }
  Collections.sort(r,(a,b)->{try{SimpleDateFormat f=new SimpleDateFormat(STAMP_PATTERN,Locale.ROOT);return f.parse(b.stamp).compareTo(f.parse(a.stamp));}catch(Exception e){return b.stamp.compareTo(a.stamp);}});return r;
 }
 public static void clearBackups()throws Exception{
  ensureBackup();
  if(run("find "+q(BACKUP_DIR)+" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} \\; && test -z \"$(find "+q(BACKUP_DIR)+" -mindepth 1 -maxdepth 1 -print -quit)\"")!=0)
   throw new IOException("Не удалось полностью очистить папку Backup");
 }
 public static final class Stage{public final File file;public final String target,backup;Stage(File f,String t,String b){file=f;target=t;backup=b;}}
 public static Stage stageForEditing(Context c,String path,boolean createBackup)throws Exception{
  String name=path.substring(path.lastIndexOf('/')+1);
  byte[]data=read(path);
  String backup=null;
  if(createBackup){ensureBackup();backup=backupVerified(path,name);}
  File staged=new File(c.getFilesDir(),"editing_"+System.currentTimeMillis()+".sav");
  try(FileOutputStream o=new FileOutputStream(staged)){o.write(data);o.getFD().sync();}
  if(staged.length()!=data.length||!Arrays.equals(data,readLocal(staged))){
   staged.delete();throw new IOException("Временная копия не прошла проверку");
  }
  // Keep the live save in pt_saves throughout editing. The game must never see an empty save folder.
  return new Stage(staged,path,backup);
 }
 private static byte[] readLocal(File file)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  try(InputStream in=new FileInputStream(file)){byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  return out.toByteArray();
 }
 public static Stage backupStage(Context c,String path)throws Exception{return stageForEditing(c,path,true);}
 public static RestoreResult restoreBackup(String backupPath)throws Exception{
  ensureBackup();String filename=backupPath.substring(backupPath.lastIndexOf('/')+1);BackupName parsed=parseBackupName(filename);
  if(parsed==null)throw new IOException("Имя резервной копии имеет неизвестный формат");
  String target=SAVE_DIR+"/"+parsed.name;byte[]data=read(backupPath);String conflict=null;
  if(exists(target))conflict=backupVerified(target,parsed.name);
  writeAtomic(target,data);
  if(!Arrays.equals(data,read(target)))throw new IOException("Проверка восстановленного сохранения не пройдена. Backup не удалён.");
  if(run("rm -- "+q(backupPath))!=0)throw new IOException("Файл восстановлен, но исходную копию из Backup не удалось удалить");
  return new RestoreResult(parsed.name,conflict);
 }
 public static void renameSaveAtomic(String oldPath,String newPath,byte[]data)throws Exception{
  if(oldPath.equals(newPath)){writeAtomic(oldPath,data);return;}
  if(!newPath.startsWith(SAVE_DIR+"/")||newPath.substring(SAVE_DIR.length()+1).contains("/")||
     newPath.endsWith("/")||newPath.substring(newPath.lastIndexOf('/')+1).trim().isEmpty())
   throw new IOException("Недопустимое имя сохранения");
  if(exists(newPath))throw new IOException("Сохранение с таким именем уже существует");
  writeAtomic(newPath,data);
  if(!Arrays.equals(data,read(newPath)))throw new IOException("Проверка нового файла не пройдена");
  if(run("rm -- "+q(oldPath))!=0)throw new IOException("Новое сохранение записано, но старый файл не удалось удалить. Оба файла сохранены.");
 }
 public static void writeAtomic(String path,byte[]data)throws Exception{
  String tmp=path+".srml_editor_tmp";Process p=sh("umask 077; cat > "+q(tmp));
  try(OutputStream o=p.getOutputStream()){o.write(data);o.flush();}int x=p.waitFor();p.destroy();
  if(x!=0)throw new IOException("Ошибка записи временного файла");
  if(!Arrays.equals(data,read(tmp))){run("rm -f -- "+q(tmp));throw new IOException("Проверка временного файла не пройдена");}
  if(run("mv -f -- "+q(tmp)+" "+q(path)+" && test -f "+q(path))!=0)throw new IOException("Не удалось вернуть файл в pt_saves");
  if(!Arrays.equals(data,read(path)))throw new IOException("Финальная проверка сохранения не пройдена. Backup сохранён.");
 }
 private ShizukuFileAccess(){}
}