package com.srmlmobile.saveeditor;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Locale;
import rikka.shizuku.Shizuku;

public final class ShizukuFileAccess {
 public static final String SAVE_DIR="/storage/emulated/0/Android/Data/com.playdigious.slimerancher/files/pt_saves";
 public static final String BACKUP_DIR="/storage/emulated/0/Android/Data/com.playdigious.slimerancher/files/Backup";
 private static Process sh(String c)throws Exception{
  java.lang.reflect.Method m=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);m.setAccessible(true);
  return (Process)m.invoke(null,new String[]{"sh","-c",c},null,null);
 }
 private static String q(String s){return "'"+s.replace("'","'\\''")+"'";}
 public static void ensureBackup()throws Exception{
  Process p=sh("mkdir -p "+q(BACKUP_DIR)+" && test -d "+q(BACKUP_DIR)+" && test -w "+q(BACKUP_DIR));
  int x=p.waitFor();p.destroy();if(x!=0)throw new IOException("Не удалось создать Backup");
 }
 public static List<String> listSaves()throws Exception{
  Process p=sh("find "+q(SAVE_DIR)+" -maxdepth 1 -type f -print 2>/dev/null");ByteArrayOutputStream o=new ByteArrayOutputStream();
  try(InputStream i=p.getInputStream()){byte[]b=new byte[4096];int n;while((n=i.read(b))!=-1)o.write(b,0,n);}
  int x=p.waitFor();p.destroy();if(x!=0)throw new IOException("Не удалось прочитать pt_saves");
  List<String>r=new ArrayList<>();for(String s:new String(o.toByteArray(),"UTF-8").split("\\r?\\n"))if(!s.trim().isEmpty())r.add(s.trim());Collections.sort(r);return r;
 }
 public static byte[] read(String path)throws Exception{
  Process p=sh("cat -- "+q(path));ByteArrayOutputStream o=new ByteArrayOutputStream();
  try(InputStream i=p.getInputStream()){byte[]b=new byte[8192];int n;while((n=i.read(b))!=-1){if(o.size()+n>64*1024*1024)throw new IOException("Файл больше 64 МБ");o.write(b,0,n);}}
  int x=p.waitFor();p.destroy();if(x!=0)throw new IOException("Не удалось прочитать "+path);return o.toByteArray();
 }
 public static final class Stage{public final File file;public final String target,backup;Stage(File f,String t,String b){file=f;target=t;backup=b;}}
 public static Stage backupStage(Context c,String path)throws Exception{
  ensureBackup();String name=path.substring(path.lastIndexOf('/')+1);
  String stamp=new SimpleDateFormat("dd.MM.yyyy HH-mm",Locale.ROOT).format(new Date());String b=BACKUP_DIR+"/"+name+" ("+stamp+")";
  Process cp=sh("cp -p -- "+q(path)+" "+q(b)+" && cmp -s -- "+q(path)+" "+q(b));int x=cp.waitFor();cp.destroy();
  if(x!=0)throw new IOException("Backup не прошёл побайтовую проверку. Оригинал не изменён.");
  byte[] data=read(path);File f=new File(c.getFilesDir(),"editing_"+System.currentTimeMillis()+".sav");
  try(FileOutputStream o=new FileOutputStream(f)){o.write(data);o.getFD().sync();}
  if(f.length()!=data.length){f.delete();throw new IOException("Временная копия не прошла проверку.");}
  Process rm=sh("rm -- "+q(path)+" && test ! -e "+q(path));x=rm.waitFor();rm.destroy();
  if(x!=0){f.delete();throw new IOException("Не удалось убрать оригинал из pt_saves. Backup сохранён.");}
  return new Stage(f,path,b);
 }
 public static void writeAtomic(String path,byte[] data)throws Exception{
  String tmp=path+".srml_editor_tmp";Process p=sh("umask 077; cat > "+q(tmp));
  try(OutputStream o=p.getOutputStream()){o.write(data);o.flush();}int x=p.waitFor();p.destroy();if(x!=0)throw new IOException("Ошибка записи временного файла");
  if(!Arrays.equals(data,read(tmp))){Process d=sh("rm -f -- "+q(tmp));d.waitFor();d.destroy();throw new IOException("Проверка временного файла не пройдена");}
  Process mv=sh("mv -f -- "+q(tmp)+" "+q(path)+" && test -f "+q(path));x=mv.waitFor();mv.destroy();if(x!=0)throw new IOException("Не удалось вернуть файл в pt_saves");
  if(!Arrays.equals(data,read(path)))throw new IOException("Финальная проверка не пройдена. Backup сохранён.");
 }
 private ShizukuFileAccess(){}
}