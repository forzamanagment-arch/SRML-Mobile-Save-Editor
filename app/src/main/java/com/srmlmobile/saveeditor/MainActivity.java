package com.srmlmobile.saveeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.view.Gravity;
import android.view.MotionEvent;
import android.animation.ValueAnimator;
import android.widget.HorizontalScrollView;
import android.view.animation.OvershootInterpolator;
import android.view.View;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
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
    private static final int INK=Color.rgb(45,55,72);
    private static final int MUTED=Color.rgb(107,123,141);
    private static final int CREAM=Color.rgb(245,240,232);
    private static final int CARD=Color.rgb(255,253,247);
    private static final int TEAL=Color.rgb(78,205,196);
    private static final int PINK=Color.rgb(255,107,157);
    private static final int LAVENDER=Color.rgb(179,157,219);
    private static final int SAND=Color.rgb(224,216,204);
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private TextView status,output;
    private LinearLayout content;
    private HorizontalScrollView pager;
    private LinearLayout pageStrip;
    private final LinearLayout[] pageContents=new LinearLayout[4];
    private int pageWidth;
    private Button shizukuButton,launchButton;
    private TextView pageTitle;
    private LinearLayout navBar,shizukuPanel;
    private final List<LinearLayout> navItems=new ArrayList<>();
    private int selectedPage=0;
    private float touchStartX,touchStartY;
    private String backupNotice;
    private boolean showingBackups=false;
    private boolean russian=false;

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
        }else show(tr("Connect Shizuku to view your saves.","Подключи Shizuku, чтобы увидеть сохранения."));
    }

    private void buildUi(){
        russian=getSharedPreferences("srml_settings",MODE_PRIVATE).getBoolean("russian",false);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(12));
        root.setBackground(new MarshmallowDrawable());

         View pinkRule=new View(this);
        pinkRule.setBackground(background(PINK,Color.TRANSPARENT,dp(4)));
        LinearLayout.LayoutParams ruleParams=new LinearLayout.LayoutParams(dp(46),dp(5));
        ruleParams.bottomMargin=dp(8);
        root.addView(pinkRule,ruleParams);
        pageTitle=text("SRML Save Editor",30,INK,true);
        pageTitle.setTypeface(GameAssets.typeface(this,true));
        pageTitle.setPadding(0,dp(3),0,dp(14));
        root.addView(pageTitle);

        shizukuPanel=new LinearLayout(this);
        shizukuPanel.setOrientation(LinearLayout.VERTICAL);
        shizukuPanel.setPadding(dp(14),dp(10),dp(14),dp(10));
        shizukuPanel.setBackground(background(CARD,SAND,dp(22)));
        status=text("",13,MUTED,false);
        shizukuPanel.addView(status);
        shizukuButton=actionButton(tr("Connect Shizuku","Подключить Shizuku"),this::requestShizuku);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(44));
        sp.topMargin=dp(8);shizukuPanel.addView(shizukuButton,sp);
        root.addView(shizukuPanel);

        launchButton=actionButton(tr("Launch game","Запустить игру"),this::launchGame);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.topMargin=dp(10);root.addView(launchButton,lp);

        pager=new HorizontalScrollView(this);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setFillViewport(true);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageWidth=getResources().getDisplayMetrics().widthPixels-dp(40);
        pageStrip=new LinearLayout(this);
        pageStrip.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<4;i++){
            ScrollView vertical=new ScrollView(this);
            vertical.setFillViewport(true);
            vertical.setVerticalScrollBarEnabled(false);
            LinearLayout pageContent=new LinearLayout(this);
            pageContent.setOrientation(LinearLayout.VERTICAL);
            pageContent.setPadding(0,dp(14),0,dp(18));
            pageContents[i]=pageContent;
            vertical.addView(pageContent,new ScrollView.LayoutParams(-1,-2));
            pageStrip.addView(vertical,new LinearLayout.LayoutParams(pageWidth,-1));
        }
        pager.addView(pageStrip,new HorizontalScrollView.LayoutParams(-2,-1));
        root.addView(pager,new LinearLayout.LayoutParams(-1,0,1));
        content=pageContents[0];
        pager.setOnScrollChangeListener((View v,int x,int y,int oldX,int oldY)->{
            int visible=Math.max(0,Math.min(3,Math.round(x/(float)pageWidth)));
            updateNavVisual(visible);
        });
        pager.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                touchStartX=event.getX();touchStartY=event.getY();return false;
            }
            if(event.getAction()==MotionEvent.ACTION_UP){
                float dx=event.getX()-touchStartX,dy=event.getY()-touchStartY;
                pager.post(()->{
                    int target=Math.max(0,Math.min(3,Math.round(pager.getScrollX()/(float)pageWidth)));
                    if(Math.abs(dx)>dp(48)&&Math.abs(dx)>Math.abs(dy)*1.15f)
                        target=Math.max(0,Math.min(3,selectedPage+(dx<0?1:-1)));
                    setPage(target);
                });
            }
            return false;
        });

        navBar=new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(8),dp(7),dp(8),dp(7));
        navBar.setBackground(background(CARD,SAND,dp(40)));
        String[] labels={"Saves","Backup","Settings","About"};
        for(int i=0;i<4;i++){
            final int index=i;
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(12),dp(7),dp(12),dp(5));
            NavIcon icon=new NavIcon(this,i);
            item.addView(icon,new LinearLayout.LayoutParams(dp(26),dp(26)));
            View dot=new View(this);
            dot.setBackground(background(TEAL,Color.TRANSPARENT,dp(8)));
            LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(5),dp(5));
            dotParams.topMargin=dp(4);item.addView(dot,dotParams);
            item.setTag(new Object[]{icon,dot});
            item.setOnClickListener(v->setPage(index));
            navItems.add(item);
            navBar.addView(item,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,dp(62));
        navParams.bottomMargin=dp(2);root.addView(navBar,navParams);
        setContentView(root);
        setPage(0);
    }

    private String tr(String en,String ru){return russian?ru:en;}
    private void launchGame(){
        Intent intent=getPackageManager().getLaunchIntentForPackage("com.playdigious.slimerancher");
        if(intent==null){Toast.makeText(this,tr("Game app was not found on this device.","Игра не найдена на этом устройстве."),Toast.LENGTH_LONG).show();return;}
        try{startActivity(intent);}catch(Throwable t){Toast.makeText(this,tr("Could not launch the game.","Не удалось запустить игру."),Toast.LENGTH_LONG).show();}
    }
    private void setPage(int page){
        page=Math.max(0,Math.min(3,page));
        selectedPage=page;
        content=pageContents[page];
        if(content!=null)content.removeAllViews();
        if(pageTitle!=null){
            String[] titles={tr("SRML Save Editor","SRML Save Editor"),tr("Backups","Резервные копии"),tr("Settings","Настройки"),tr("About","О приложении")};
            pageTitle.setText(titles[page]);
        }
        if(output!=null)output.setVisibility(page<2?View.VISIBLE:View.GONE);
        if(shizukuPanel!=null)shizukuPanel.setVisibility(page<2?View.VISIBLE:View.GONE);
        if(launchButton!=null)launchButton.setVisibility(page==0?View.VISIBLE:View.GONE);
        updateNavVisual(page);
        if(pager!=null){
            int target=page*pageWidth;
            ValueAnimator animator=ValueAnimator.ofInt(pager.getScrollX(),target);
            animator.setDuration(360);
            animator.setInterpolator(new OvershootInterpolator(0.55f));
            animator.addUpdateListener(v->pager.scrollTo((Integer)v.getAnimatedValue(),0));
            animator.start();
        }
        if(page==0)showWorlds();
        else if(page==1)showBackups();
        else if(page==2)renderSettings();
        else renderAbout();
    }
    private void updateNavVisual(int page){
        for(int i=0;i<navItems.size();i++){
            LinearLayout item=navItems.get(i);
            Object[] parts=(Object[])item.getTag();
            NavIcon icon=(NavIcon)parts[0];View dot=(View)parts[1];
            boolean active=i==page;
            icon.setActive(active);dot.setVisibility(active?View.VISIBLE:View.INVISIBLE);
            item.setBackground(active?background(Color.rgb(232,248,245),Color.TRANSPARENT,dp(24)):background(Color.TRANSPARENT,Color.TRANSPARENT,dp(24)));
        }
    }
    private void renderSettings(){
        content.removeAllViews();
        addSectionCard(tr("Preferences","Настройки"),tr("Choose how SRML Save Editor is displayed.","Настрой отображение SRML Save Editor."));
        addSectionCard(tr("Language","Язык"),tr("App interface language","Язык интерфейса приложения"));
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.HORIZONTAL);
        Button en=actionButton("English",()->setLanguage(false));
        Button ru=actionButton("Русский",()->setLanguage(true));
        en.setBackground(background(russian?Color.rgb(232,224,212):TEAL,Color.TRANSPARENT,dp(24)));
        ru.setBackground(background(russian?TEAL:Color.rgb(232,224,212),Color.TRANSPARENT,dp(24)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.rightMargin=dp(7);choices.addView(en,p);
        choices.addView(ru,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(choices);
        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(20),dp(18),dp(20),dp(18));
        info.setBackground(background(Color.rgb(31,41,55),Color.TRANSPARENT,dp(24)));
        TextView infoTitle=text(tr("BACKUP SAFETY","ЗАЩИТА РЕЗЕРВНЫХ КОПИЙ"),11,Color.rgb(255,154,209),true);
        infoTitle.setLetterSpacing(0.08f);info.addView(infoTitle);
        TextView infoBody=text(tr("A backup is created before a save is edited. Restore it at any time from the Backup page.","Перед редактированием создаётся резервная копия. Восстановить её можно во вкладке Backup."),14,Color.WHITE,false);
        infoBody.setPadding(0,dp(8),0,0);info.addView(infoBody);
        LinearLayout.LayoutParams infoParams=new LinearLayout.LayoutParams(-1,-2);infoParams.topMargin=dp(14);content.addView(info,infoParams);
    }
    private void setLanguage(boolean ru){
        russian=ru;getSharedPreferences("srml_settings",MODE_PRIVATE).edit().putBoolean("russian",ru).apply();setPage(2);
        refreshStatus();
        if(shizukuButton!=null)shizukuButton.setText(tr("Connect Shizuku","Подключить Shizuku"));
        if(launchButton!=null)launchButton.setText(tr("Launch game","Запустить игру"));
    }
    private void renderAbout(){
        content.removeAllViews();
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);card.setPadding(dp(24),dp(30),dp(24),dp(30));
        card.setBackground(background(CARD,SAND,dp(28)));
        card.addView(new SlimeLogoView(this),new LinearLayout.LayoutParams(dp(82),dp(82)));
        TextView name=text("SRML Save Editor",24,INK,true);name.setTypeface(GameAssets.typeface(this,true));name.setPadding(0,dp(17),0,dp(4));card.addView(name);
        card.addView(text("Version "+BuildConfig.VERSION_NAME,13,Color.rgb(155,168,181),false));
        TextView description=text(tr("A cozy little tool inspired by the world of Slime Rancher. Built with love and a lot of plorts.","Уютный маленький инструмент, вдохновлённый миром Slime Rancher. Создан с любовью и кучей плортов."),15,MUTED,false);
        description.setGravity(Gravity.CENTER);description.setPadding(0,dp(18),0,0);card.addView(description);
        content.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addSectionCard(String title,String subtitle){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(16),dp(18),dp(16));card.setBackground(background(CARD,SAND,dp(24)));
        TextView h=text(title,18,INK,true);card.addView(h);
        TextView s=text(subtitle,13,MUTED,false);s.setPadding(0,dp(5),0,0);card.addView(s);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);content.addView(card,p);
    }
    private class SlimeLogoView extends View{
        private final Paint p=new Paint(3);
        SlimeLogoView(Activity a){super(a);}
        @Override protected void onDraw(Canvas canvas){
            float s=Math.min(getWidth(),getHeight())/82f;
            canvas.save();canvas.scale(s,s);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(224,248,243));canvas.drawCircle(41,41,40,p);
            Path blob=new Path();blob.moveTo(15,52);blob.cubicTo(16,34,24,21,41,20);blob.cubicTo(58,20,67,34,68,52);blob.cubicTo(68,62,57,68,41,68);blob.cubicTo(25,68,14,62,15,52);blob.close();
            p.setColor(TEAL);canvas.drawPath(blob,p);
            p.setColor(INK);canvas.drawCircle(32,46,2.5f,p);canvas.drawCircle(50,46,2.5f,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f);p.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(35,48,47,57,15,150,false,p);
            canvas.restore();
        }
    }
    private class NavIcon extends View{
        private final Paint paint=new Paint(3);private final int type;private boolean active;
        NavIcon(Activity a,int t){super(a);type=t;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
        void setActive(boolean a){active=a;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);paint.setColor(active?TEAL:Color.rgb(155,168,181));
            float s=getWidth()/26f;canvas.save();canvas.scale(s,s);
            if(type==0){
                canvas.drawRoundRect(4,3,22,23,2,2,paint);canvas.drawLine(8,3,8,9,paint);canvas.drawLine(18,3,18,9,paint);
                canvas.drawRoundRect(8,14,18,23,1,1,paint);canvas.drawLine(10,17,16,17,paint);
            }else if(type==1){
                canvas.drawRoundRect(3,8,23,22,2,2,paint);canvas.drawLine(3,8,13,13,paint);canvas.drawLine(23,8,13,13,paint);canvas.drawLine(13,13,13,22,paint);
                canvas.drawLine(7,4,19,4,paint);canvas.drawLine(7,4,7,8,paint);canvas.drawLine(19,4,19,8,paint);
            }else if(type==2){
                canvas.drawCircle(13,13,4,paint);for(int a=0;a<8;a++){double q=a*Math.PI/4;float x=(float)Math.cos(q),y=(float)Math.sin(q);canvas.drawLine(13+x*7,13+y*7,13+x*10,13+y*10,paint);}
            }else{
                canvas.drawCircle(13,13,10,paint);canvas.drawLine(13,11,13,18,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(13,7,1.2f,paint);paint.setStyle(Paint.Style.STROKE);
            }
            canvas.restore();
        }
    }
    private final class MarshmallowDrawable extends android.graphics.drawable.Drawable{
        private final Paint p=new Paint(3);
        @Override public void draw(Canvas canvas){
            android.graphics.Rect b=getBounds();float w=b.width(),h=b.height();
            p.setShader(new android.graphics.LinearGradient(0,0,w,h,CREAM,Color.rgb(237,232,220),android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b,p);p.setShader(null);
            p.setShader(new android.graphics.RadialGradient(w*.88f,h*.08f,w*.48f,new int[]{0x55FF9AD1,0x00FF9AD1},null,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b,p);p.setShader(null);
            p.setShader(new android.graphics.RadialGradient(w*.12f,h*.88f,w*.42f,new int[]{0x554ECDC4,0x004ECDC4},null,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b,p);p.setShader(null);
            p.setShader(new android.graphics.RadialGradient(w*.58f,h*.78f,w*.34f,new int[]{0x44B39DDB,0x00B39DDB},null,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b,p);p.setShader(null);
            p.setColor(0x147B6AA8);p.setStyle(Paint.Style.FILL);
            for(int y=24;y<h;y+=54)for(int x=20;x<w;x+=58)canvas.drawCircle(x+(y%3)*5,y,1.5f,p);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    private GradientDrawable background(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);
        if(stroke!=Color.TRANSPARENT)d.setStroke(dp(1),stroke);return d;
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(GameAssets.typeface(this,bold));return t;
    }
    private Button actionButton(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(Color.WHITE);b.setTypeface(GameAssets.typeface(this,true));
        b.setBackground(background(TEAL,Color.TRANSPARENT,dp(28)));
        if(label.equals("Launch game")||label.equals("Запустить игру")){
            android.graphics.Bitmap gameIcon=GameAssets.icon(this,"PinkSlime","Slime");
            if(gameIcon!=null){
                BitmapDrawable drawable=new BitmapDrawable(getResources(),gameIcon);
                drawable.setBounds(0,0,dp(26),dp(26));
                b.setCompoundDrawables(drawable,null,null,null);
                b.setCompoundDrawablePadding(dp(8));
            }
        }
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
        if(status!=null)status.setText(binder?(granted?tr("● Shizuku connected","● Shizuku подключён"):tr("● Shizuku permission required","● Требуется разрешение Shizuku")):tr("● Shizuku is not connected","● Shizuku не подключён"));
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
        showingBackups=false;scanSaves();
    }
    private void showBackups(){
        showingBackups=true;
        if(!hasPermission()){show(tr("Connect Shizuku to view backups.","Подключи Shizuku для просмотра резервных копий."));refreshStatus();return;}
        show(tr("Loading backups…","Загружаю резервные копии…"));
        executor.execute(()->{
            try{
                List<ShizukuFileAccess.BackupItem>items=ShizukuFileAccess.listBackups();
                runOnUiThread(()->renderBackups(items));
            }catch(Throwable t){AppLog.exception(this,"BACKUP","Backup list failed",t);runOnUiThread(()->show("Не удалось открыть Backup: "+t.getMessage()));}
        });
    }
    private void renderBackups(List<ShizukuFileAccess.BackupItem>items){
        LinearLayout content=pageContents[1];
        content.removeAllViews();
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(gameIconTile("Key",38),new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView headingText=text(tr("Your backups","Резервные копии"),22,INK,true);
        headingText.setPadding(dp(10),0,0,0);heading.addView(headingText);
        content.addView(heading);
        TextView hint=text(tr("Choose a backup to restore it to your worlds.","Выбери копию, чтобы восстановить её в список миров."),13,MUTED,false);
        hint.setPadding(0,dp(4),0,dp(12));content.addView(hint);
        if(items.isEmpty()){
            addEmptyCard(tr("No backups yet","Пока нет резервных копий"),tr("Backups appear here after you edit a save.","Копии появятся здесь после редактирования миров."),content);
            if(backupNotice!=null){show(backupNotice);backupNotice=null;}else show(tr("Backups: 0","Резервных копий: 0"));return;
        }
        for(ShizukuFileAccess.BackupItem item:items){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(20),dp(18),dp(20),dp(18));card.setBackground(background(CARD,SAND,dp(26)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);content.addView(card,cp);
            LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(LinearLayout.HORIZONTAL);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(gameIconTile("Key",34),new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView backupName=text(item.originalName,16,INK,true);backupName.setPadding(dp(10),0,0,0);
            titleRow.addView(backupName,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(titleRow);
            TextView date=text(item.stamp,13,MUTED,false);date.setPadding(0,dp(3),0,dp(8));card.addView(date);
            Button restore=actionButton(tr("Restore backup","Восстановить"),()->confirmRestore(item));
            card.addView(restore,new LinearLayout.LayoutParams(-1,dp(46)));
        }
        if(backupNotice!=null){show(backupNotice);backupNotice=null;}else show(tr("Backups: ","Резервных копий: ")+items.size());
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
                        backupNotice="Сохранение «"+result.restoredName+"» успешно восстановлено из Backup.\n\nВ pt_saves уже был файл с таким именем. Он сохранён в Backup под именем:\n"+new File(result.conflictBackupPath).getName()+"\n\nКонфликт имён предотвращён.";
                    }else backupNotice="Сохранение «"+result.restoredName+"» успешно восстановлено из Backup.";
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
        showingBackups=false;show(tr("Loading worlds…","Загружаю список миров…"));
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
        LinearLayout content=pageContents[0];
        content.removeAllViews();
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(gameIconTile("Conservatory",38),new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView headingText=text(tr("Your worlds","Твои миры"),22,INK,true);
        headingText.setPadding(dp(10),0,0,0);heading.addView(headingText);
        content.addView(heading);
        TextView hint=text(tr("Choose a world to edit its save.","Выбери мир, чтобы изменить сохранение."),13,MUTED,false);hint.setPadding(0,dp(4),0,dp(12));content.addView(hint);
        if(found.isEmpty()){addEmptyCard(tr("No worlds found","Миры не найдены"),tr("Make sure the game has created a save.","Проверь, что игра уже создала сохранение."),content);show("Список миров обновлён.");return;}
        for(SaveSlot slot:found){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(background(CARD,SAND,dp(26)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);content.addView(card,cp);
            LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(LinearLayout.HORIZONTAL);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(gameIconTile("Conservatory",34),new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView worldName=text(slot.displayName,16,INK,true);worldName.setPadding(dp(10),0,0,0);
            titleRow.addView(worldName,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(titleRow);
            TextView detail=text(slot.fileName+"  •  "+slot.sizeText,12,MUTED,false);detail.setPadding(0,dp(3),0,dp(8));card.addView(detail);
            card.addView(actionButton(tr("Edit save","Редактировать"),()->beginEditing(slot)),new LinearLayout.LayoutParams(-1,dp(46)));
        }
        show(tr("Worlds found: ","Найдено миров: ")+found.size());
    }
    private View gameIconTile(String hint,int size){
        FrameLayout tile=new FrameLayout(this);
        tile.setBackground(background(Color.rgb(232,220,200),Color.rgb(203,191,168),dp(15)));
        android.graphics.Bitmap bitmap=GameAssets.icon(this,hint);
        if(bitmap!=null){
            ImageView image=new ImageView(this);image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(image,new FrameLayout.LayoutParams(dp(size),dp(size),Gravity.CENTER));
        }else{
            TextView unknown=text("?",22,TEAL,true);unknown.setGravity(Gravity.CENTER);
            tile.addView(unknown,new FrameLayout.LayoutParams(-1,-1));
        }
        return tile;
    }
    private void addEmptyCard(String title,String description,LinearLayout parent){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(20),dp(20),dp(20),dp(20));
        card.setBackground(background(CARD,SAND,dp(18)));
        card.addView(text(title,17,INK,true));TextView d=text(description,13,MUTED,false);d.setPadding(0,dp(5),0,0);card.addView(d);
        parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
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