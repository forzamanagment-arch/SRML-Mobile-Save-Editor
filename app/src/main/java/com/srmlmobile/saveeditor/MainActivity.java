package com.srmlmobile.saveeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE=4201;
    private static final int MAX_FILE_BYTES=64*1024*1024;
    private static final int INK=Color.rgb(57,75,105);
    private static final int MUTED=Color.rgb(119,119,111);
    private static final int CREAM=Color.rgb(248,240,221);
    private static final int CARD=Color.rgb(255,251,240);
    private static final int TEAL=Color.rgb(43,181,199);
    private static final int PINK=Color.rgb(232,82,139);
    private static final int SAND=Color.rgb(225,207,174);
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private TextView status,output;
    private LinearLayout content;
    private Button worldsTab,backupTab,shizukuButton;
    private String backupNotice;
    private boolean showingBackups=false;

    private final Shizuku.OnRequestPermissionResultListener permissionListener=(code,result)->runOnUiThread(()->{
        if(code==REQUEST_CODE){
            refreshStatus();
            if(result==PackageManager.PERMISSION_GRANTED){
                executor.execute(()->{try{ShizukuFileAccess.ensureBackup();}catch(Throwable t){AppLog.exception(this,"BACKUP","Backup check failed",t);}});
                scanSaves();
            }else show("Нужно разрешение Shizuku для доступа к сохранениям.");
        }
    });
    private final Shizuku.OnBinderReceivedListener binderReceivedListener=()->runOnUiThread(this::refreshStatus);
    private final Shizuku.OnBinderDeadListener binderDeadListener=()->runOnUiThread(this::refreshStatus);

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        AppLog.i(this,"APP","Started Android="+android.os.Build.VERSION.SDK_INT+", device="+android.os.Build.MODEL+", version="+BuildConfig.VERSION_NAME);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        buildUi();
        refreshStatus();
        if(hasPermission()){
            executor.execute(()->{try{ShizukuFileAccess.ensureBackup();}catch(Throwable t){AppLog.exception(this,"BACKUP","Backup directory check failed",t);}});
            scanSaves();
        }else show("Подключи Shizuku, чтобы увидеть сохранения.");
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(22),dp(18),dp(14));
        root.setBackgroundColor(CREAM);

        TextView brand=new TextView(this);
        brand.setText("SLIME RANCHER 2");
        brand.setTextColor(TEAL);
        brand.setTextSize(12);
        brand.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        brand.setLetterSpacing(0.12f);
        root.addView(brand);

        TextView title=new TextView(this);
        title.setText("Save Editor");
        title.setTextColor(INK);
        title.setTextSize(29);
        title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        title.setPadding(0,dp(2),0,dp(10));
        root.addView(title);

        status=new TextView(this);
        status.setTextColor(INK);
        status.setTextSize(13);
        status.setPadding(dp(13),dp(10),dp(13),dp(10));
        status.setBackground(background(CARD,SAND,dp(16)));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        shizukuButton=actionButton("Подключить Shizuku",this::requestShizuku);
        LinearLayout.LayoutParams shizukuParams=new LinearLayout.LayoutParams(-1,dp(44));
        shizukuParams.topMargin=dp(8);root.addView(shizukuButton,shizukuParams);

        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0,dp(14),0,dp(12));
        worldsTab=tabButton("МИРЫ",true);
        backupTab=tabButton("BACKUP",false);
        tabs.addView(worldsTab,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(48),1);
        bp.leftMargin=dp(8);tabs.addView(backupTab,bp);
        worldsTab.setOnClickListener(v->showWorlds());
        backupTab.setOnClickListener(v->showBackups());
        root.addView(tabs);

        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0,0,0,dp(10));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        output=new TextView(this);
        output.setTextColor(INK);
        output.setTextSize(13);
        output.setPadding(dp(4),dp(8),dp(4),dp(4));
        root.addView(output);
        setContentView(root);
        renderWorldsEmpty();
    }

    private Button tabButton(String text,boolean active){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        b.setTextColor(active?Color.WHITE:INK);b.setBackground(background(active?TEAL:SAND,Color.TRANSPARENT,dp(18)));
        return b;
    }
    private void setTabState(boolean backups){
        showingBackups=backups;
        if(worldsTab!=null){worldsTab.setBackground(background(backups?SAND:TEAL,Color.TRANSPARENT,dp(18)));worldsTab.setTextColor(backups?INK:Color.WHITE);}
        if(backupTab!=null){backupTab.setBackground(background(backups?TEAL:SAND,Color.TRANSPARENT,dp(18)));backupTab.setTextColor(backups?Color.WHITE:INK);}
    }
    private GradientDrawable background(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);
        if(stroke!=Color.TRANSPARENT)d.setStroke(dp(1),stroke);return d;
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));return t;
    }
    private Button actionButton(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(Color.WHITE);b.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        b.setBackground(background(TEAL,Color.TRANSPARENT,dp(16)));
        b.setMinHeight(dp(46));b.setOnClickListener(v->action.run());return b;
    }
    private void show(String message){if(output!=null)output.setText(message);}
    private boolean hasPermission(){
        try{return Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}
        catch(Throwable t){AppLog.exception(this,"SHIZUKU","Permission check failed",t);return false;}
    }
    private void refreshStatus(){
        boolean binder=false,granted=false;
        try{binder=Shizuku.pingBinder();granted=binder&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}
        catch(Throwable ignored){}
        if(status!=null)status.setText(binder?(granted?"● Shizuku подключён":"● Shizuku: нужно разрешение"):"● Shizuku не подключён");
        if(shizukuButton!=null)shizukuButton.setVisibility(granted?View.GONE:View.VISIBLE);
    }
    private void requestShizuku(){
        try{
            if(!Shizuku.pingBinder()){show("Запусти Shizuku и повтори подключение.");refreshStatus();}
            else if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){scanSaves();}
            else Shizuku.requestPermission(REQUEST_CODE);
        }catch(Throwable t){show("Не удалось подключить Shizuku: "+t.getClass().getSimpleName());}
    }
    private Process startShell(String command)throws Exception{
        java.lang.reflect.Method m=Shizuku.class.getDeclaredMethod("newProcess",String[].class,String[].class,String.class);
        m.setAccessible(true);return (Process)m.invoke(null,new String[]{"sh","-c",command},null,null);
    }
    private static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    private byte[] readRemoteFile(String path)throws Exception{
        Process p=startShell("cat -- "+quote(path));
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=p.getInputStream();byte[]b=new byte[8192];int n;
            while((n=in.read(b))!=-1){if(out.size()+n>MAX_FILE_BYTES)throw new IllegalStateException("Файл больше 64 МБ");out.write(b,0,n);}
            int x=p.waitFor();if(x!=0)throw new IOException("Не удалось прочитать "+path);return out.toByteArray();
        }finally{p.destroy();}
    }
    private byte[] readLocalFile(File file)throws Exception{
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX_FILE_BYTES)throw new IOException("Архив больше 64 МБ");out.write(b,0,n);}return out.toByteArray();
        }
    }
    private void showWorlds(){
        setTabState(false);scanSaves();
    }
    private void showBackups(){
        setTabState(true);
        if(!hasPermission()){show("Подключи Shizuku для просмотра резервных копий.");refreshStatus();return;}
        show("Загружаю резервные копии…");
        executor.execute(()->{
            try{
                List<ShizukuFileAccess.BackupItem>items=ShizukuFileAccess.listBackups();
                runOnUiThread(()->renderBackups(items));
            }catch(Throwable t){AppLog.exception(this,"BACKUP","Backup list failed",t);runOnUiThread(()->show("Не удалось открыть Backup: "+t.getMessage()));}
        });
    }
    private void renderBackups(List<ShizukuFileAccess.BackupItem>items){
        content.removeAllViews();
        content.addView(text("Резервные копии",22,INK,true));
        TextView hint=text("Выбери копию и восстанови её в список миров.",13,MUTED,false);
        hint.setPadding(0,dp(4),0,dp(12));content.addView(hint);
        if(items.isEmpty()){
            addEmptyCard("Пока нет резервных копий","Копии появятся здесь после редактирования миров.");
            show("Резервных копий: 0");return;
        }
        for(ShizukuFileAccess.BackupItem item:items){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(background(CARD,SAND,dp(18)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);content.addView(card,cp);
            card.addView(text(item.originalName,16,INK,true));
            TextView date=text(item.stamp,13,MUTED,false);date.setPadding(0,dp(3),0,dp(8));card.addView(date);
            Button restore=actionButton("Восстановить",()->confirmRestore(item));
            card.addView(restore,new LinearLayout.LayoutParams(-1,dp(46)));
        }
        show("Резервных копий: "+items.size());
    }
    private void confirmRestore(ShizukuFileAccess.BackupItem item){
        new AlertDialog.Builder(this)
            .setTitle("Восстановить сохранение?")
            .setMessage(item.originalName+"\nДата копирования: "+item.stamp+"\n\nЕсли в pt_saves уже есть файл с таким именем, он сначала будет сохранён в Backup.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Восстановить",(d,w)->restore(item))
            .show();
    }
    private void restore(ShizukuFileAccess.BackupItem item){
        show("Восстанавливаю сохранение…");
        executor.execute(()->{
            try{
                ShizukuFileAccess.RestoreResult result=ShizukuFileAccess.restoreBackup(item.path);
                runOnUiThread(()->{
                    if(result.hadConflict()){
                        show("Сохранение «"+result.restoredName+"» успешно восстановлено из Backup.\n\nВ pt_saves уже был файл с таким именем. Он сохранён в Backup под именем:\n"+new File(result.conflictBackupPath).getName()+"\n\nКонфликт имён предотвращён.");
                    }else show("Сохранение «"+result.restoredName+"» успешно восстановлено из Backup.");
                    showBackups();
                });
            }catch(Throwable t){
                AppLog.exception(this,"BACKUP_RESTORE","Restore failed",t);
                runOnUiThread(()->show("Не удалось восстановить сохранение: "+t.getMessage()+"\nИсходная резервная копия не удаляется при ошибке проверки."));
            }
        });
    }
    private void scanSaves(){
        if(!hasPermission()){show("Сначала подключи Shizuku и выдай разрешение.");refreshStatus();return;}
        showingBackups=false;show("Загружаю список миров…");
        executor.execute(()->{
            try{
                ShizukuFileAccess.ensureBackup();
                String command="if [ -d "+quote(ShizukuFileAccess.SAVE_DIR)+" ] && [ -r "+quote(ShizukuFileAccess.SAVE_DIR)+" ]; then find "+quote(ShizukuFileAccess.SAVE_DIR)+" -maxdepth 1 -type f -print 2>/dev/null; else echo __NO_ACCESS__; fi";
                Process p=startShell(command);ByteArrayOutputStream out=new ByteArrayOutputStream();
                try{byte[]b=new byte[4096];int n;while((n=p.getInputStream().read(b))!=-1)out.write(b,0,n);int x=p.waitFor();if(x!=0)throw new IOException("Не удалось прочитать pt_saves");}finally{p.destroy();}
                String listing=new String(out.toByteArray(),StandardCharsets.UTF_8);
                if(listing.contains("__NO_ACCESS__"))throw new IOException("Папка pt_saves недоступна через Shizuku");
                List<SaveSlot>found=new ArrayList<>();File tempDir=new File(getCacheDir(),"save_scan_tmp");
                if(!tempDir.exists()&&!tempDir.mkdirs())throw new IOException("Не удалось создать временную папку");
                try{
                    for(String line:listing.split("\\r?\\n")){
                        String path=line.trim();if(path.isEmpty())continue;
                        File temp=new File(tempDir,new File(path).getName()+".zip");
                        try{
                            byte[]remote=readRemoteFile(path);
                            try(FileOutputStream o=new FileOutputStream(temp)){o.write(remote);o.getFD().sync();}
                            SaveSlot slot=SaveSlotReader.inspect(path,readLocalFile(temp));
                            if(slot!=null)found.add(slot);
                        }catch(Throwable e){AppLog.w(this,"SAVE_SCAN","Skipped "+path+": "+e);}
                        finally{if(temp.exists())temp.delete();}
                    }
                }finally{File[]left=tempDir.listFiles();if(left!=null)for(File x:left)x.delete();tempDir.delete();}
                runOnUiThread(()->renderSaves(found));
            }catch(Throwable t){AppLog.exception(this,"SAVE_SCAN","Save scan failed",t);runOnUiThread(()->show("Не удалось прочитать список миров: "+t.getMessage()));}
        });
    }
    private void renderSaves(List<SaveSlot>found){
        content.removeAllViews();
        content.addView(text("Твои миры",22,INK,true));
        TextView hint=text("Выбери мир, чтобы изменить сохранение.",13,MUTED,false);hint.setPadding(0,dp(4),0,dp(12));content.addView(hint);
        if(found.isEmpty()){addEmptyCard("Миры не найдены","Проверь, что игра уже создала сохранение.");show("Список миров обновлён.");return;}
        for(SaveSlot slot:found){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(background(CARD,SAND,dp(18)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);content.addView(card,cp);
            card.addView(text(slot.displayName,16,INK,true));
            TextView detail=text(slot.fileName+"  •  "+slot.sizeText,12,MUTED,false);detail.setPadding(0,dp(3),0,dp(8));card.addView(detail);
            card.addView(actionButton("Редактировать",()->beginEditing(slot)),new LinearLayout.LayoutParams(-1,dp(46)));
        }
        show("Найдено миров: "+found.size());
    }
    private void addEmptyCard(String title,String description){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));
        card.setBackground(background(CARD,SAND,dp(18)));
        card.addView(text(title,17,INK,true));TextView d=text(description,13,MUTED,false);d.setPadding(0,dp(5),0,0);card.addView(d);
        content.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }
    private void beginEditing(SaveSlot slot){
        if(!hasPermission()){show("Нет доступа Shizuku.");requestShizuku();return;}
        show("Создаю резервную копию…");
        executor.execute(()->{
            try{
                String path=slot.fileName.startsWith("/")?slot.fileName:ShizukuFileAccess.SAVE_DIR+"/"+slot.fileName;
                ShizukuFileAccess.Stage stage=ShizukuFileAccess.backupStage(this,path);
                AppLog.i(this,"SAVE_EDIT","Backup created "+stage.backup);
                runOnUiThread(()->{
                    Intent intent=new Intent(this,SaveEditorActivity.class);
                    intent.putExtra("stage_path",stage.file.getAbsolutePath());
                    intent.putExtra("target_path",stage.target);
                    intent.putExtra("backup_path",stage.backup);
                    startActivity(intent);
                });
            }catch(Throwable t){AppLog.exception(this,"SAVE_EDIT","Could not stage save",t);runOnUiThread(()->show("Не удалось подготовить сохранение: "+t.getMessage()));}
        });
    }
    private void showLogs(){
        String logs=AppLog.read(this);
        new AlertDialog.Builder(this).setTitle("Журнал").setMessage(logs.length()==0?"Журнал пуст.":logs)
            .setPositiveButton("Закрыть",null).show();
    }
    private void shareLogs(){
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,AppLog.read(this));
        startActivity(Intent.createChooser(i,"Поделиться журналом"));
    }
    @Override protected void onDestroy(){
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        executor.shutdownNow();super.onDestroy();
    }
}