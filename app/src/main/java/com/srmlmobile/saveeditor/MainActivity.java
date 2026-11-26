package com.srmlmobile.saveeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.InputType;
import android.widget.EditText;
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
    private static final int TEAL=Color.rgb(49,73,110);
    private static final int PINK=Color.rgb(255,107,157);
    private static final int LAVENDER=Color.rgb(179,157,219);
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private TextView status,output;
    private LinearLayout content;
    private HorizontalScrollView pager;
    private LinearLayout pageStrip;
    private final LinearLayout[] pageContents=new LinearLayout[4];
    private int pageWidth;
    private Button shizukuButton,overlayPermissionButton;
    private TextView overlayStatus;
    private LinearLayout permissionPanel;
    private int homeMode=0;
    private final android.os.Handler navHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable beginNavDrag=()->navDragging=true;
    private boolean navDragging=false;
    private float navTouchStartX;
    private boolean darkTheme=true;
    private LinearLayout navBar,shizukuPanel;
    private FrameLayout navContainer;
    private View navHighlight;
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
                if(homeMode==1)scanSaves();
            }
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
        }
    }

    private void buildUi(){
        navItems.clear();
        russian=getSharedPreferences("srml_settings",MODE_PRIVATE).getBoolean("russian",false);
        darkTheme=getSharedPreferences("srml_settings",MODE_PRIVATE).getBoolean("dark_theme",true);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(16)+getStatusBarInset(),dp(20),dp(12));
        root.setBackground(new MarshmallowDrawable());
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(creamColor()));
        getWindow().setStatusBarColor(creamColor());
        getWindow().getDecorView().setSystemUiVisibility(darkTheme?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        shizukuPanel=new LinearLayout(this);
        shizukuPanel.setOrientation(LinearLayout.VERTICAL);
        shizukuPanel.setPadding(dp(14),dp(10),dp(14),dp(10));
        shizukuPanel.setBackground(background(cardColor(),borderColor(),dp(22)));
        status=text("",13,mutedColor(),false);
        shizukuPanel.addView(status);
        shizukuButton=actionButton(tr("Connect Shizuku","Подключить Shizuku"),this::requestShizuku);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(44));
        sp.topMargin=dp(8);shizukuPanel.addView(shizukuButton,sp);
        permissionPanel=new LinearLayout(this);
        permissionPanel.setOrientation(LinearLayout.VERTICAL);
        permissionPanel.addView(shizukuPanel);
        LinearLayout overlayPanel=new LinearLayout(this);
        overlayPanel.setOrientation(LinearLayout.VERTICAL);
        overlayPanel.setPadding(dp(14),dp(10),dp(14),dp(10));
        overlayPanel.setBackground(background(cardColor(),borderColor(),dp(22)));
        overlayStatus=text("",13,mutedColor(),false);
        overlayPanel.addView(overlayStatus);
        overlayPermissionButton=actionButton(tr("Grant overlay permission","Выдать разрешение оверлея"),this::requestOverlayPermission);
        LinearLayout.LayoutParams overlayButtonParams=new LinearLayout.LayoutParams(-1,dp(44));
        overlayButtonParams.topMargin=dp(8);
        overlayPanel.addView(overlayPermissionButton,overlayButtonParams);
        LinearLayout.LayoutParams overlayPanelParams=new LinearLayout.LayoutParams(-1,-2);
        overlayPanelParams.topMargin=dp(9);
        permissionPanel.addView(overlayPanel,overlayPanelParams);
        root.addView(permissionPanel);

        pager=new HorizontalScrollView(this);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setFillViewport(true);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setBackgroundColor(Color.TRANSPARENT);
        pager.setOnTouchListener((v,event)->true);
        pageWidth=getResources().getDisplayMetrics().widthPixels-dp(40);
        pageStrip=new LinearLayout(this);
        pageStrip.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<4;i++){
            ScrollView vertical=new ScrollView(this);
            vertical.setFillViewport(true);
            vertical.setVerticalScrollBarEnabled(false);
            vertical.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout pageContent=new LinearLayout(this);
            pageContent.setOrientation(LinearLayout.VERTICAL);
            pageContent.setPadding(0,dp(14),0,dp(18));
            pageContent.setBackgroundColor(Color.TRANSPARENT);
            pageContents[i]=pageContent;
            vertical.addView(pageContent,new ScrollView.LayoutParams(-1,-2));
            pageStrip.addView(vertical,new LinearLayout.LayoutParams(pageWidth,-1));
        }
        pager.addView(pageStrip,new HorizontalScrollView.LayoutParams(-2,-1));
        root.addView(pager,new LinearLayout.LayoutParams(-1,0,1));
        content=pageContents[0];
        navContainer=new FrameLayout(this);
        navContainer.setBackground(background(cardColor(),borderColor(),dp(34)));
        navHighlight=new View(this);
        navHighlight.setBackground(gradientBackground(darkTheme?Color.rgb(37,54,79):Color.rgb(231,237,246),darkTheme?Color.rgb(31,45,66):Color.rgb(210,220,235),Color.TRANSPARENT,dp(28)));
        FrameLayout.LayoutParams highlightParams=new FrameLayout.LayoutParams(dp(64),dp(48),Gravity.TOP|Gravity.LEFT);
        highlightParams.leftMargin=dp(5);highlightParams.topMargin=dp(5);
        navContainer.addView(navHighlight,highlightParams);
        navBar=new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER);
        navBar.setPadding(dp(5),dp(5),dp(5),dp(5));
        navBar.setBackgroundColor(Color.TRANSPARENT);
        String[] iconFiles={"HomeIcon.png","BackupTurquoiseIcon.FIXED.png","SettingsTurquoiseIcon.png","InfoTurquoiseIcon.png"};
        for(int i=0;i<4;i++){
            final int index=i;
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(5),dp(3),dp(5),dp(3));
            ImageView icon=new ImageView(this);
            android.graphics.Bitmap bitmap=GameAssets.exactIcon(this,iconFiles[i]);
            if(bitmap!=null)icon.setImageBitmap(bitmap);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setPadding(dp(2),dp(2),dp(2),dp(2));
            item.addView(icon,new LinearLayout.LayoutParams(dp(i==1?35:30),dp(i==1?35:30)));
            item.setTag(icon);
            item.setOnClickListener(v->setPage(index));
            item.setOnTouchListener((v,event)->{
                if(index!=selectedPage)return false;
                if(event.getAction()==MotionEvent.ACTION_DOWN){
                    navTouchStartX=event.getRawX();
                    navDragging=false;
                    navHandler.removeCallbacks(beginNavDrag);
                    navHandler.postDelayed(beginNavDrag,190);
                    return true;
                }
                if(event.getAction()==MotionEvent.ACTION_MOVE){
                    if(navDragging&&navHighlight!=null&&navBar!=null){
                        int[] location=new int[2];navContainer.getLocationOnScreen(location);
                        float localX=event.getRawX()-location[0];
                        float max=navBar.getWidth()-navHighlight.getWidth()-dp(5);
                        float tx=Math.max(0,Math.min(max,localX-navHighlight.getWidth()/2f-dp(5)));
                        navHighlight.setTranslationX(tx);
                    }
                    return true;
                }
                if(event.getAction()==MotionEvent.ACTION_UP){
                    navHandler.removeCallbacks(beginNavDrag);
                    if(navDragging){
                        int[] location=new int[2];navBar.getLocationOnScreen(location);
                        float x=event.getRawX()-location[0];
                        int width=Math.max(1,navBar.getWidth()/4);
                        int target=Math.max(0,Math.min(3,(int)(x/width)));
                        navDragging=false;
                        setPage(target);
                    }else{
                        navDragging=false;
                        v.performClick();
                    }
                    return true;
                }
                if(event.getAction()==MotionEvent.ACTION_CANCEL){
                    navHandler.removeCallbacks(beginNavDrag);
                    navDragging=false;
                    updateNavVisual(selectedPage);
                    return true;
                }
                return true;
            });
            navItems.add(item);
            navBar.addView(item,new LinearLayout.LayoutParams(0,dp(52),1));
        }
        navContainer.addView(navBar,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,dp(62));
        navParams.bottomMargin=dp(2);root.addView(navContainer,navParams);
        setContentView(root);
        setPage(0);
        refreshStatus();
        updateOverlayStatus();
    }

    @Override protected void onResume(){
        super.onResume();
        refreshStatus();
        updateOverlayStatus();
    }

    private int getStatusBarInset(){
        int resource=getResources().getIdentifier("status_bar_height","dimen","android");
        return resource>0?getResources().getDimensionPixelSize(resource):dp(24);
    }
    private String tr(String en,String ru){return russian?ru:en;}
    private int inkColor(){return darkTheme?Color.rgb(244,242,236):Color.rgb(45,55,72);}
    private int mutedColor(){return darkTheme?Color.rgb(177,187,198):Color.rgb(107,123,141);}
    private int creamColor(){return darkTheme?Color.rgb(17,20,25):Color.rgb(245,240,232);}
    private int cardColor(){return darkTheme?Color.rgb(34,39,46):Color.rgb(255,253,247);}
    private int borderColor(){return darkTheme?Color.rgb(65,73,83):Color.rgb(224,216,204);}
    private int surfaceAlt(){return darkTheme?Color.rgb(43,49,58):Color.rgb(245,240,232);}
    private void addHeader(String title,String iconFile){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this);android.graphics.Bitmap bitmap=GameAssets.exactIcon(this,iconFile);
        if(bitmap!=null)icon.setImageBitmap(bitmap);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(TEAL,android.graphics.PorterDuff.Mode.SRC_IN);
        row.addView(icon,new LinearLayout.LayoutParams(dp(34),dp(34)));
        TextView label=text(title,22,inkColor(),true);label.setPadding(dp(10),0,0,0);row.addView(label);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(12);content.addView(row,params);
    }
    private void setDarkTheme(boolean enabled){
        getSharedPreferences("srml_settings",MODE_PRIVATE).edit().putBoolean("dark_theme",enabled).apply();
        buildUi();
        setPage(2);
        refreshStatus();
    }
    private static final String GAME_PACKAGE = "com.playdigious.slimerancher";

    private void launchGame(){
        AppLog.i(this,"GAME_LAUNCH","Checking native game package: "+GAME_PACKAGE);
        try{
            getPackageManager().getPackageInfo(GAME_PACKAGE,0);
            Intent intent=getPackageManager().getLaunchIntentForPackage(GAME_PACKAGE);
            if(intent!=null){
                AppLog.i(this,"GAME_LAUNCH","Found installed game package "+GAME_PACKAGE+"; launching");
                startActivity(intent);
                return;
            }
            AppLog.e(this,"GAME_LAUNCH","Package is installed but has no launcher intent: "+GAME_PACKAGE);
            showLaunchTargetDialog(tr("The game package is installed, but Android did not return a launch activity.","Пакет игры установлен, но Android не вернул экран запуска."));
        }catch(PackageManager.NameNotFoundException e){
            AppLog.exception(this,"GAME_LAUNCH","PackageNotFound: "+GAME_PACKAGE,e);
            showLaunchTargetDialog(tr("The default game package was not visible or was not installed. Choose an installed app or enter its package name.","Пакет игры не найден или скрыт Android. Выбери установленное приложение либо введи имя пакета."));
        }catch(SecurityException e){
            AppLog.exception(this,"GAME_LAUNCH","PermissionDenied while checking "+GAME_PACKAGE,e);
            showLaunchTargetDialog(tr("Android denied access to the app information. Check app visibility settings.","Android запретил доступ к сведениям приложения. Проверь видимость пакетов."));
        }catch(Throwable t){
            AppLog.exception(this,"GAME_LAUNCH","Unexpected launch error for "+GAME_PACKAGE,t);
            showLaunchTargetDialog(tr("Could not launch the game: ","Не удалось запустить игру: ")+t.getClass().getSimpleName());
        }
    }

    private void launchPackage(String packageName){
        String target=packageName==null?"":packageName.trim();
        if(target.isEmpty()){
            showLaunchTargetDialog(tr("Enter a package name first.","Сначала введи имя пакета."));
            return;
        }
        try{
            getPackageManager().getPackageInfo(target,0);
            Intent intent=getPackageManager().getLaunchIntentForPackage(target);
            if(intent==null){
                AppLog.e(this,"GAME_LAUNCH","Installed package has no launch intent: "+target);
                showLaunchTargetDialog(tr("This package is installed but has no launcher activity.","Этот пакет установлен, но у него нет экрана запуска."));
                return;
            }
            AppLog.i(this,"GAME_LAUNCH","Launching manually selected package: "+target);
            startActivity(intent);
        }catch(PackageManager.NameNotFoundException e){
            AppLog.exception(this,"GAME_LAUNCH","PackageNotFound for manual target: "+target,e);
            showLaunchTargetDialog(tr("Package not found: ","Пакет не найден: ")+target);
        }catch(SecurityException e){
            AppLog.exception(this,"GAME_LAUNCH","PermissionDenied for manual target: "+target,e);
            showLaunchTargetDialog(tr("Permission denied for package: ","Нет доступа к пакету: ")+target);
        }catch(Throwable t){
            AppLog.exception(this,"GAME_LAUNCH","Manual launch failed for "+target,t);
            showLaunchTargetDialog(tr("Launch failed: ","Ошибка запуска: ")+t.getClass().getSimpleName());
        }
    }

    private void showLaunchTargetDialog(String reason){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(8),dp(18),dp(18));
        card.setBackground(background(cardColor(),borderColor(),dp(28)));
        View pinkBar=new View(this);
        pinkBar.setBackground(background(PINK,Color.TRANSPARENT,dp(8)));
        card.addView(pinkBar,new LinearLayout.LayoutParams(-1,dp(6)));
        TextView title=text(tr("Choose game app","Выбрать приложение игры"),21,inkColor(),true);
        title.setPadding(0,dp(14),0,dp(6));
        card.addView(title);
        TextView message=text(reason,13,mutedColor(),false);
        message.setPadding(0,0,0,dp(10));
        card.addView(message);
        TextView packageLabel=text(tr("Package name","Имя пакета"),12,inkColor(),true);
        card.addView(packageLabel);
        EditText packageInput=new EditText(this);
        packageInput.setSingleLine(true);
        packageInput.setText(GAME_PACKAGE);
        packageInput.setTextSize(14);
        packageInput.setInputType(InputType.TYPE_CLASS_TEXT);
        packageInput.setTextColor(inkColor());
        packageInput.setHintTextColor(mutedColor());
        packageInput.setPadding(dp(12),dp(8),dp(12),dp(8));
        packageInput.setBackground(background(cardColor(),borderColor(),dp(14)));
        LinearLayout.LayoutParams inputParams=new LinearLayout.LayoutParams(-1,dp(46));
        inputParams.topMargin=dp(5);
        card.addView(packageInput,inputParams);

        TextView appsTitle=text(tr("Matching installed apps","Подходящие установленные приложения"),12,inkColor(),true);
        appsTitle.setPadding(0,dp(12),0,dp(5));
        card.addView(appsTitle);
        LinearLayout appList=new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        Intent launcherQuery=new Intent(Intent.ACTION_MAIN);
        launcherQuery.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables=getPackageManager().queryIntentActivities(launcherQuery,0);
        int shown=0;
        for(ResolveInfo candidate:launchables){
            String pkg=candidate.activityInfo.packageName;
            String label=String.valueOf(candidate.loadLabel(getPackageManager()));
            String searchable=(pkg+" "+label).toLowerCase(java.util.Locale.ROOT);
            if(!(searchable.contains("slime")||searchable.contains("rancher")||
                 searchable.contains("monomi")||searchable.contains("playdigious")||
                 searchable.contains("winlator")||searchable.contains("mobox")||
                 searchable.contains("exagear"))) continue;
            TextView app=text(label+"  ·  "+pkg,12,inkColor(),false);
            app.setPadding(dp(10),dp(8),dp(10),dp(8));
            app.setBackground(background(Color.rgb(245,240,232),borderColor(),dp(12)));
            LinearLayout.LayoutParams appParams=new LinearLayout.LayoutParams(-1,-2);
            appParams.bottomMargin=dp(4);
            appList.addView(app,appParams);
            app.setOnClickListener(v->packageInput.setText(pkg));
            shown++;
        }
        if(shown==0){
            TextView none=text(tr("No matching launcher apps were found. You can still enter a package above.","Подходящие приложения не найдены. Имя пакета всё равно можно ввести вручную."),11,mutedColor(),false);
            appList.addView(none);
        }
        ScrollView appScroll=new ScrollView(this);
        appScroll.setFillViewport(false);
        appScroll.addView(appList);
        card.addView(appScroll,new LinearLayout.LayoutParams(-1,dp(112)));

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        Button cancel=new Button(this);
        cancel.setText(tr("Cancel","Отмена"));
        cancel.setAllCaps(false);
        cancel.setTextColor(TEAL);
        cancel.setTypeface(GameAssets.typeface(this,true));
        cancel.setBackground(background(Color.TRANSPARENT,TEAL,dp(28)));
        Button launch=new Button(this);
        launch.setText(tr("Launch","Запустить"));
        launch.setAllCaps(false);
        launch.setTextColor(Color.WHITE);
        launch.setTypeface(GameAssets.typeface(this,true));
        launch.setBackground(background(PINK,Color.TRANSPARENT,dp(28)));
        LinearLayout.LayoutParams cancelParams=new LinearLayout.LayoutParams(0,dp(44),1);
        cancelParams.rightMargin=dp(8);
        actions.addView(cancel,cancelParams);
        actions.addView(launch,new LinearLayout.LayoutParams(0,dp(44),1));
        LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-1,-2);
        actionParams.topMargin=dp(12);
        card.addView(actions,actionParams);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(card).create();
        cancel.setOnClickListener(v->dialog.dismiss());
        launch.setOnClickListener(v->{String pkg=packageInput.getText().toString();dialog.dismiss();launchPackage(pkg);});
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params=dialog.getWindow().getAttributes();
            params.dimAmount=0.45f;
            dialog.getWindow().setAttributes(params);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(32),-2);
        }
    }
    private void setPage(int page){
        page=Math.max(0,Math.min(3,page));
        selectedPage=page;
        content=pageContents[page];
        if(content!=null)content.removeAllViews();
        if(output!=null)output.setVisibility(page==0?View.VISIBLE:View.GONE);
        if(permissionPanel!=null)permissionPanel.setVisibility(page==0&&homeMode==0?View.VISIBLE:View.GONE);
        updateNavVisual(page);
        if(pager!=null){
            int target=page*pageWidth;
            ValueAnimator animator=ValueAnimator.ofInt(pager.getScrollX(),target);
            animator.setDuration(360);
            animator.setInterpolator(new OvershootInterpolator(0.55f));
            animator.addUpdateListener(v->pager.scrollTo((Integer)v.getAnimatedValue(),0));
            animator.start();
        }
        if(page==0){
            if(homeMode==0)renderHome();
            else if(homeMode==1)scanSaves();
            else renderModMenuControls();
        }
        else if(page==1)showBackups();
        else if(page==2)renderSettings();
        else renderAbout();
    }
    private void updateNavVisual(int page){
        for(int i=0;i<navItems.size();i++){
            LinearLayout item=navItems.get(i);
            ImageView icon=(ImageView)item.getTag();
            boolean active=i==page;
            if(icon!=null){
                if(active){icon.setColorFilter(darkTheme?Color.rgb(64,126,234):TEAL,android.graphics.PorterDuff.Mode.SRC_IN);icon.setAlpha(1f);}
                else{icon.setColorFilter(Color.rgb(145,151,160),android.graphics.PorterDuff.Mode.SRC_IN);icon.setAlpha(0.88f);}
            }
            item.setBackgroundColor(Color.TRANSPARENT);
        }
        if(navBar!=null&&navHighlight!=null){
            navBar.post(()->{
                if(page<0||page>=navItems.size())return;
                View target=navItems.get(page);
                float x=navBar.getX()+target.getX()+target.getWidth()*0.08f;
                float width=target.getWidth()*0.84f;
                FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)navHighlight.getLayoutParams();
                lp.width=(int)width;lp.height=dp(50);lp.topMargin=dp(6);
                navHighlight.setLayoutParams(lp);
                navHighlight.animate().translationX(x-dp(5)).setDuration(280).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            });
        }
    }
    private void renderSettings(){
        content.removeAllViews();
        addHeader(tr("Settings","Настройки"),"SettingsTurquoiseIcon.png");
        addSectionCard(tr("Appearance","Оформление"),tr("Choose the light or dark look for the whole editor.","Выбери светлое или тёмное оформление всего редактора."));
        LinearLayout themeChoices=new LinearLayout(this);themeChoices.setOrientation(LinearLayout.HORIZONTAL);
        Button light=actionButton(tr("Light theme","Светлая тема"),()->setDarkTheme(false));
        Button dark=actionButton(tr("Dark theme","Тёмная тема"),()->setDarkTheme(true));
        light.setBackground(gradientBackground(darkTheme?surfaceAlt():TEAL,Color.rgb(39,58,88),Color.TRANSPARENT,dp(24)));
        dark.setBackground(gradientBackground(darkTheme?TEAL:surfaceAlt(),darkTheme?Color.rgb(39,58,88):Color.rgb(220,215,207),Color.TRANSPARENT,dp(24)));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(48),1);tp.rightMargin=dp(7);
        themeChoices.addView(light,tp);themeChoices.addView(dark,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(themeChoices);
        View languageGap=new View(this);content.addView(languageGap,new LinearLayout.LayoutParams(1,dp(14)));
        addSectionCard(tr("Language","Язык"),tr("App interface language","Язык интерфейса приложения"));
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.HORIZONTAL);
        Button en=actionButton("English",()->setLanguage(false));
        Button ru=actionButton("Русский",()->setLanguage(true));
        en.setBackground(gradientBackground(russian?surfaceAlt():TEAL,russian?darken(surfaceAlt(),0.08f):Color.rgb(39,58,88),Color.TRANSPARENT,dp(24)));
        ru.setBackground(gradientBackground(russian?TEAL:surfaceAlt(),russian?Color.rgb(39,58,88):darken(surfaceAlt(),0.08f),Color.TRANSPARENT,dp(24)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.rightMargin=dp(7);choices.addView(en,p);
        choices.addView(ru,new LinearLayout.LayoutParams(0,dp(48),1));content.addView(choices);
        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(20),dp(18),dp(20),dp(18));
        info.setBackground(gradientBackground(darkTheme?Color.rgb(38,43,52):Color.rgb(31,41,55),darkTheme?Color.rgb(27,31,38):Color.rgb(24,32,44),Color.TRANSPARENT,dp(24)));
        TextView infoTitle=text(tr("BACKUP SAFETY","ЗАЩИТА РЕЗЕРВНЫХ КОПИЙ"),11,Color.rgb(255,154,209),true);
        infoTitle.setLetterSpacing(0.08f);info.addView(infoTitle);
        TextView infoBody=text(tr("A backup is optional and is created only when you choose it before editing. The original save stays in the game folder.","Резервная копия создаётся только по твоему выбору перед редактированием. Исходное сохранение остаётся в папке игры."),14,Color.WHITE,false);
        infoBody.setPadding(0,dp(8),0,0);info.addView(infoBody);
        LinearLayout.LayoutParams infoParams=new LinearLayout.LayoutParams(-1,-2);infoParams.topMargin=dp(14);content.addView(info,infoParams);
    }
    private void setLanguage(boolean ru){
        russian=ru;getSharedPreferences("srml_settings",MODE_PRIVATE).edit().putBoolean("russian",ru).apply();setPage(2);
        refreshStatus();
        if(shizukuButton!=null)shizukuButton.setText(tr("Connect Shizuku","Подключить Shizuku"));
        updateOverlayStatus();
        if(selectedPage==0){
            if(homeMode==0)renderHome();
            else if(homeMode==2)renderModMenuControls();
        }
    }
    private void renderAbout(){
        content.removeAllViews();
        addHeader(tr("About","О приложении"),"InfoTurquoiseIcon.png");
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(24),dp(26),dp(24),dp(26));
        card.setBackground(background(cardColor(),borderColor(),dp(28)));
        ImageView slimeIcon=new ImageView(this);
        android.graphics.Bitmap saberSlime=GameAssets.icon(this,"Saber_Slime_SP");
        if(saberSlime!=null)slimeIcon.setImageBitmap(saberSlime);
        slimeIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(slimeIcon,new LinearLayout.LayoutParams(dp(82),dp(82)));
        TextView name=text("SRML Game Tools",24,inkColor(),true);name.setTypeface(GameAssets.typeface(this,true));name.setPadding(0,dp(17),0,dp(4));card.addView(name);
        card.addView(text("Version "+BuildConfig.VERSION_NAME,13,Color.rgb(155,168,181),false));
        TextView description=text(tr("An Android toolkit for Slime Rancher Mobile. Edit saves, manage inventory and backups, and explore game data.","Набор инструментов для Slime Rancher Mobile: редактирование сохранений, инвентаря, резервных копий и исследование игровых данных."),15,mutedColor(),false);
        description.setGravity(Gravity.START);description.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);description.setPadding(0,dp(16),0,0);card.addView(description);
        LinearLayout.LayoutParams aboutCardParams=new LinearLayout.LayoutParams(-1,-2);aboutCardParams.bottomMargin=dp(16);content.addView(card,aboutCardParams);

        addSectionCard(tr("Authors & credits","Авторы и благодарности"),
            tr("Project creator and developer: forzamanagment-arch.\nDevelopment assistance: ChatGPT (OpenAI).\nSlime Rancher is developed by Monomi Park. Game names and original game assets belong to their respective owners.","Создатель и разработчик проекта: forzamanagment-arch.\nПомощь в разработке: ChatGPT (OpenAI).\nSlime Rancher разработана Monomi Park. Названия и оригинальные игровые материалы принадлежат их правообладателям."));
        addSectionCard(tr("How it works","Как это работает"),
            tr("The app uses Shizuku permission to access the Slime Rancher Mobile save folder. It reads the save archive, locates the gamesave-sav data, and exposes supported values for editing. Before editing, you can choose whether to create an original backup in the Backups tab. Unknown save data is intended to remain unchanged.","Приложение использует разрешение Shizuku для доступа к папке сохранений Slime Rancher Mobile. Оно читает архив сохранения, находит данные gamesave-sav и показывает поддерживаемые значения для редактирования. Перед редактированием можно выбрать, создавать ли исходную копию во вкладке Backups. Неизвестные данные сохранения не должны изменяться."));
        addSectionCard(tr("What you need","Что потребуется"),
            tr("Install Slime Rancher Mobile and Shizuku, start the Shizuku service, then grant this app permission. Close the game before editing saves. This app edits save files; it does not modify the game APK or inject code into the game.","Установи Slime Rancher Mobile и Shizuku, запусти службу Shizuku и выдай приложению разрешение. Перед редактированием закрой игру. Приложение изменяет файлы сохранений, а не APK игры и не внедряет код в игру."));
        addSectionCard(tr("Backup location","Где хранятся копии"),
            tr("Backups are stored in the game's files directory under Backup. If a restore would overwrite a save with the same name, the existing file is preserved first.","Резервные копии хранятся в каталоге файлов игры, в папке Backup. Если восстановление заменит сохранение с таким же именем, существующий файл сначала сохраняется отдельно."));
        TextView shizukuTitle=text(tr("Shizuku fork by thedjchi","Форк Shizuku от thedjchi"),18,inkColor(),true);
        shizukuTitle.setPadding(dp(2),dp(4),dp(2),dp(8));content.addView(shizukuTitle);
        Button shizukuLink=actionButton("Open GitHub Releases  ↗",()->openExternalUrl("https://github.com/thedjchi/Shizuku/releases/latest"));
        content.addView(shizukuLink,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private void openExternalUrl(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Throwable t){Toast.makeText(this,"Unable to open link: "+t.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private View glyphTile(View glyph,int size){
        FrameLayout tile=new FrameLayout(this);
        tile.setBackground(background(surfaceAlt(),borderColor(),dp(15)));
        tile.addView(glyph,new FrameLayout.LayoutParams(dp(size),dp(size),Gravity.CENTER));
        return tile;
    }
    private View assetIconTile(String filename,int size){
        FrameLayout tile=new FrameLayout(this);
        tile.setBackground(background(surfaceAlt(),borderColor(),dp(15)));
        ImageView icon=new ImageView(this);
        android.graphics.Bitmap bitmap=GameAssets.exactIcon(this,filename);
        if(bitmap!=null)icon.setImageBitmap(bitmap);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon,new FrameLayout.LayoutParams(dp(size),dp(size),Gravity.CENTER));
        return tile;
    }
    private void addSectionCard(String title,String subtitle){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(16),dp(18),dp(16));card.setBackground(background(cardColor(),borderColor(),dp(24)));
        TextView h=text(title,18,inkColor(),true);card.addView(h);
        TextView s=text(subtitle,13,mutedColor(),false);s.setPadding(0,dp(5),0,0);card.addView(s);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);content.addView(card,p);
    }
    private class DocumentGlyph extends View{
        private final Paint paint=new Paint(3);
        DocumentGlyph(Activity activity){super(activity);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float scale=Math.min(getWidth(),getHeight())/26f;
            canvas.save();canvas.translate((getWidth()-26f*scale)/2f,(getHeight()-26f*scale)/2f);canvas.scale(scale,scale);
            paint.setColor(PINK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.9f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            Path page=new Path();page.moveTo(7,3);page.lineTo(16,3);page.lineTo(21,8);page.lineTo(21,23);page.lineTo(7,23);page.close();canvas.drawPath(page,paint);
            canvas.drawLine(16,3,16,8,paint);canvas.drawLine(16,8,21,8,paint);
            canvas.drawLine(10,13,18,13,paint);canvas.drawLine(10,17,18,17,paint);
            canvas.restore();
        }
    }
    private class BackupGlyph extends View{
        private final Paint paint=new Paint(3);
        BackupGlyph(Activity activity){super(activity);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float scale=Math.min(getWidth(),getHeight())/26f;
            canvas.save();canvas.translate((getWidth()-26f*scale)/2f,(getHeight()-26f*scale)/2f);canvas.scale(scale,scale);
            paint.setColor(TEAL);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.9f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawRoundRect(3,10,23,22,2,2,paint);
            canvas.drawLine(3,13,13,18,paint);canvas.drawLine(23,13,13,18,paint);
            canvas.drawLine(13,18,13,22,paint);
            canvas.drawArc(5,2,21,18,205,230,false,paint);
            Path arrow=new Path();arrow.moveTo(5,3);arrow.lineTo(5,8);arrow.lineTo(10,7);canvas.drawPath(arrow,paint);
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
    private class SaveGlyph extends View{
        private final Paint paint=new Paint(3);
        SaveGlyph(Activity activity){super(activity);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float scale=Math.min(getWidth(),getHeight())/26f;
            canvas.save();
            canvas.translate((getWidth()-26f*scale)/2f,(getHeight()-26f*scale)/2f);
            canvas.scale(scale,scale);
            paint.setColor(TEAL);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawRoundRect(3,3,23,23,2,2,paint);
            canvas.drawLine(7,3,7,10,paint);
            canvas.drawLine(17,3,17,10,paint);
            canvas.drawRoundRect(8,14,18,23,1,1,paint);
            canvas.drawLine(10,17,16,17,paint);
            canvas.restore();
        }
    }
    private final class MarshmallowDrawable extends android.graphics.drawable.Drawable{
        private final Paint p=new Paint(3);
        @Override public void draw(Canvas canvas){
            android.graphics.Rect b=getBounds();float w=b.width(),h=b.height();
            int base=creamColor();
            int lower=darkTheme?Color.rgb(13,16,20):Color.rgb(237,232,220);
            p.setShader(new android.graphics.LinearGradient(0,0,w,h,base,lower,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b,p);p.setShader(null);
            p.setShader(new android.graphics.RadialGradient(w*.88f,h*.08f,w*.48f,new int[]{0x55FF9AD1,0x00FF9AD1},null,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b,p);p.setShader(null);
            p.setShader(new android.graphics.RadialGradient(w*.12f,h*.88f,w*.42f,new int[]{0x5531496E,0x0031496E},null,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b,p);p.setShader(null);
            p.setShader(new android.graphics.RadialGradient(w*.58f,h*.78f,w*.34f,new int[]{0x44B39DDB,0x00B39DDB},null,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(b,p);p.setShader(null);
            p.setColor(darkTheme?0x1631496E:0x147B6AA8);p.setStyle(Paint.Style.FILL);
            for(int y=24;y<h;y+=54)for(int x=20;x<w;x+=58)canvas.drawCircle(x+(y%3)*5,y,1.5f,p);
            p.setShader(new android.graphics.RadialGradient(w*.08f,h*.28f,w*.22f,new int[]{darkTheme?0x2631496E:0x20FF8FBE,0x00000000},null,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawCircle(w*.08f,h*.28f,w*.22f,p);p.setShader(null);
            p.setShader(new android.graphics.RadialGradient(w*.94f,h*.62f,w*.25f,new int[]{darkTheme?0x2231496E:0x1E31496E,0x00000000},null,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawCircle(w*.94f,h*.62f,w*.25f,p);p.setShader(null);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    private GradientDrawable background(int fill,int stroke,int radius){
        if(fill==Color.TRANSPARENT){GradientDrawable plain=new GradientDrawable();plain.setColor(fill);plain.setCornerRadius(radius);if(stroke!=Color.TRANSPARENT)plain.setStroke(dp(1),stroke);return plain;}
        return gradientBackground(fill,darken(fill,0.055f),stroke,radius);
    }
    private GradientDrawable gradientBackground(int top,int bottom,int stroke,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});
        d.setCornerRadius(radius);if(stroke!=Color.TRANSPARENT)d.setStroke(dp(1),stroke);return d;
    }
    private int darken(int color,float amount){
        return Color.rgb(Math.max(0,(int)(Color.red(color)*(1f-amount))),Math.max(0,(int)(Color.green(color)*(1f-amount))),Math.max(0,(int)(Color.blue(color)*(1f-amount))));
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
        updateOverlayStatus();
    }
    private void requestShizuku(){
        try{
            if(!Shizuku.pingBinder()){show(tr("Start the Shizuku service, then try again.","Запусти службу Shizuku и повтори попытку."));refreshStatus();}
            else if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED){refreshStatus();}
            else Shizuku.requestPermission(REQUEST_CODE);
        }catch(Throwable t){show(tr("Could not connect Shizuku: ","Не удалось подключить Shizuku: ")+t.getClass().getSimpleName());}
    }

    private boolean canDrawOverlay(){
        return Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission(){
        if(canDrawOverlay()){updateOverlayStatus();return;}
        try{
            Intent intent=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
            startActivity(intent);
        }catch(Throwable t){show(tr("Could not open overlay settings.","Не удалось открыть настройки оверлея."));}
    }

    private void updateOverlayStatus(){
        boolean granted=canDrawOverlay();
        if(overlayStatus!=null)overlayStatus.setText(granted
            ?tr("● Overlay permission granted","● Разрешение оверлея выдано")
            :tr("● Overlay permission required","● Требуется разрешение оверлея"));
        if(overlayPermissionButton!=null){
            overlayPermissionButton.setText(granted?tr("Overlay permission granted","Разрешение оверлея выдано"):tr("Grant overlay permission","Выдать разрешение оверлея"));
            overlayPermissionButton.setEnabled(!granted);
            overlayPermissionButton.setAlpha(granted?0.65f:1f);
        }
    }

    private void startLiveMenu(){
        if(!canDrawOverlay()){
            requestOverlayPermission();
            return;
        }
        try{
            Intent intent=new Intent(this,LiveMenuOverlayService.class);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
            Toast.makeText(this,tr("Mod Menu started","Mod Menu запущено"),Toast.LENGTH_SHORT).show();
        }catch(Throwable t){
            show(tr("Could not start Mod Menu: ","Не удалось запустить Mod Menu: ")+t.getClass().getSimpleName());
        }
    }

    private void stopLiveMenu(){
        stopService(new Intent(this,LiveMenuOverlayService.class));
        Toast.makeText(this,tr("Mod Menu stopped","Mod Menu остановлено"),Toast.LENGTH_SHORT).show();
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
        homeMode=1;
        scanSaves();
    }

    private void renderHome(){
        content.removeAllViews();
        addHeader(tr("Game Tools","Инструменты игры"),"HomeIcon.png");
        addToolCard("SRML Save Editor",
            tr("Browse your existing worlds and edit supported save data, inventory and player values.","Открой список миров и редактируй поддерживаемые данные сохранения, инвентарь и параметры игрока."),
            "SaveTurquoiseIcon.png",tr("Open Save Editor","Открыть Save Editor"),()->{
                homeMode=1;
                scanSaves();
                if(permissionPanel!=null)permissionPanel.setVisibility(View.GONE);
            });
        addToolCard("SRML Mod Menu (Cheat)",
            tr("Open a floating prototype menu with editable player values, inventory fields and toggle controls. Game changes are not connected yet.","Открой плавающее меню-прототип с полями параметров игрока, редактором инвентаря и переключателями. Изменение игры пока не подключено."),
            "SettingsTurquoiseIcon.png",tr("Open Mod Menu","Открыть Mod Menu"),()->{
                homeMode=2;
                if(permissionPanel!=null)permissionPanel.setVisibility(View.GONE);
                renderModMenuControls();
            });
    }

    private void addToolCard(String title,String description,String iconName,String buttonLabel,Runnable action){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(15),dp(16),dp(14));
        card.setBackground(background(cardColor(),borderColor(),dp(24)));
        Button open=actionButton(title,action);
        open.setCompoundDrawablesWithIntrinsicBounds(0,0,0,0);
        open.setBackground(background(PINK,Color.TRANSPARENT,dp(25)));
        card.addView(open,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView detail=text(description,13,mutedColor(),false);
        detail.setPadding(dp(3),dp(10),dp(3),0);
        card.addView(detail);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(12);
        content.addView(card,params);
    }

    private void renderModMenuControls(){
        content.removeAllViews();
        addHeader("SRML Mod Menu (Cheat)","SettingsTurquoiseIcon.png");
        Button back=actionButton(tr("Back","Назад"),()->{homeMode=0;setPage(0);});
        back.setBackground(background(TEAL,Color.TRANSPARENT,dp(25)));
        LinearLayout.LayoutParams backParams=new LinearLayout.LayoutParams(-1,dp(44));
        backParams.bottomMargin=dp(14);
        content.addView(back,backParams);
        addSectionCard(tr("Floating Mod Menu","Плавающее Mod Menu"),
            tr("Start the prototype overlay over Slime Rancher Mobile. Stop it here when you are finished. The controls are visual only and do not change game values.","Запусти прототип оверлея поверх Slime Rancher Mobile. Здесь же его можно остановить. Элементы управления пока только визуальные и не меняют игровые значения."));
        Button start=actionButton(tr("Start Mod Menu","Запустить Mod Menu"),this::startLiveMenu);
        start.setBackground(background(PINK,Color.TRANSPARENT,dp(26)));
        content.addView(start,new LinearLayout.LayoutParams(-1,dp(50)));
        Button stop=actionButton(tr("Stop Mod Menu","Остановить Mod Menu"),this::stopLiveMenu);
        LinearLayout.LayoutParams stopParams=new LinearLayout.LayoutParams(-1,dp(50));
        stopParams.topMargin=dp(9);
        content.addView(stop,stopParams);
        addSectionCard(tr("Prototype status","Состояние прототипа"),
            tr("All fields, buttons and switches in the floating menu are interactive, but are not connected to the game runtime yet.","Все поля, кнопки и переключатели плавающего меню интерактивны, но пока не связаны с игровым процессом."));
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
        addHeader(tr("Backups","Резервные копии"),"BackupTurquoiseIcon.FIXED.png");
        TextView hint=text(tr("Choose a backup to restore it to your worlds.","Выбери копию, чтобы восстановить её в список миров."),13,mutedColor(),false);
        hint.setPadding(0,dp(4),0,dp(12));content.addView(hint);
        Button clearFolder=actionButton(tr("Clear backup folder","Очистить папку Backup"),this::confirmClearBackups);
        clearFolder.setBackground(background(PINK,Color.TRANSPARENT,dp(28)));
        LinearLayout.LayoutParams clearParams=new LinearLayout.LayoutParams(-1,dp(44));
        clearParams.bottomMargin=dp(12);
        content.addView(clearFolder,clearParams);
        if(items.isEmpty()){
            addEmptyCard(tr("No backups yet","Пока нет резервных копий"),tr("Backups appear here after you edit a save.","Копии появятся здесь после редактирования миров."),content);
            if(backupNotice!=null){show(backupNotice);backupNotice=null;}else show(tr("Backups: 0","Резервных копий: 0"));return;
        }
        for(ShizukuFileAccess.BackupItem item:items){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(20),dp(18),dp(20),dp(18));card.setBackground(background(cardColor(),borderColor(),dp(26)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);content.addView(card,cp);
            LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(LinearLayout.HORIZONTAL);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(assetIconTile("DocumentsPinkIcon.png",30),new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView backupName=text(item.originalName,16,inkColor(),true);backupName.setPadding(dp(10),0,0,0);
            titleRow.addView(backupName,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(titleRow);
            TextView date=text(item.stamp,13,mutedColor(),false);date.setPadding(0,dp(3),0,dp(8));card.addView(date);
            Button restore=actionButton(tr("Restore backup","Восстановить"),()->confirmRestore(item));
            restore.setBackground(background(PINK,Color.TRANSPARENT,dp(28)));
            card.addView(restore,new LinearLayout.LayoutParams(-1,dp(46)));
        }
        if(backupNotice!=null){show(backupNotice);backupNotice=null;}else show(tr("Backups: ","Резервных копий: ")+items.size());
    }
    private void confirmRestore(ShizukuFileAccess.BackupItem item){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(8),dp(18),dp(18));
        card.setBackground(background(cardColor(),borderColor(),dp(28)));
        View pinkBar=new View(this);
        pinkBar.setBackground(background(PINK,Color.TRANSPARENT,dp(8)));
        card.addView(pinkBar,new LinearLayout.LayoutParams(-1,dp(6)));

        TextView title=text(tr("Restore this save?","Восстановить сохранение?"),22,inkColor(),true);
        title.setPadding(0,dp(16),0,dp(10));
        card.addView(title);

        TextView file=text(item.originalName,13,Color.rgb(31,41,55),true);
        file.setPadding(dp(12),dp(8),dp(12),dp(8));
        file.setBackground(background(darkTheme?surfaceAlt():Color.rgb(232,237,246),TEAL,dp(18)));
        card.addView(file);

        TextView details=text(
            tr("Copied: ","Дата копирования: ")+item.stamp+
            tr("\n\nThe selected copy will be returned to pt_saves. If a save with the same name already exists, the current file is first preserved in Backup.","\n\nВыбранная копия будет возвращена в pt_saves. Если файл с таким именем уже существует, текущий файл сначала будет сохранён в Backup."),
            14,mutedColor(),false);
        details.setPadding(0,dp(12),0,dp(16));
        card.addView(details);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        Button cancel=new Button(this);
        cancel.setText(tr("Cancel","Отмена"));
        cancel.setAllCaps(false);
        cancel.setTextSize(13);
        cancel.setTypeface(GameAssets.typeface(this,true));
        cancel.setTextColor(TEAL);
        cancel.setBackground(background(Color.TRANSPARENT,TEAL,dp(28)));
        Button restore=new Button(this);
        restore.setText(tr("Restore","Восстановить"));
        restore.setAllCaps(false);
        restore.setTextSize(13);
        restore.setTypeface(GameAssets.typeface(this,true));
        restore.setTextColor(Color.WHITE);
        restore.setBackground(background(PINK,Color.TRANSPARENT,dp(28)));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(44),1);
        cp.rightMargin=dp(8);
        actions.addView(cancel,cp);
        actions.addView(restore,new LinearLayout.LayoutParams(0,dp(44),1));
        card.addView(actions);

        AlertDialog dialog=new AlertDialog.Builder(this).setView(card).create();
        cancel.setOnClickListener(v->dialog.dismiss());
        restore.setOnClickListener(v->{dialog.dismiss();restore(item);});
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params=dialog.getWindow().getAttributes();
            params.dimAmount=0.45f;
            dialog.getWindow().setAttributes(params);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(32),-2);
        }
    }
    private void confirmClearBackups(){
        new AlertDialog.Builder(this)
            .setTitle(tr("Clear backup folder?","Очистить папку Backup?"))
            .setMessage(tr(
                "All files inside Backup will be permanently deleted. This cannot be undone.",
                "Все файлы внутри Backup будут удалены без возможности восстановления."
            ))
            .setNegativeButton(tr("Cancel","Отмена"),(d,w)->d.dismiss())
            .setPositiveButton(tr("Clear","Очистить"),(d,w)->clearBackups())
            .show();
    }
    private void clearBackups(){
        if(!hasPermission()){show(tr("Connect Shizuku first.","Сначала подключи Shizuku."));return;}
        show(tr("Clearing backup folder…","Очищаю папку Backup…"));
        executor.execute(()->{
            try{
                ShizukuFileAccess.clearBackups();
                backupNotice=tr("Backup folder cleared.","Папка Backup очищена.");
                runOnUiThread(this::showBackups);
            }catch(Throwable t){
                AppLog.exception(this,"BACKUP_CLEAR","Could not clear backup folder",t);
                runOnUiThread(()->show(tr("Could not clear Backup: ","Не удалось очистить Backup: ")+t.getMessage()));
            }
        });
    }
    private void restore(ShizukuFileAccess.BackupItem item){
        show(tr("Restoring save…","Восстанавливаю сохранение…"));
        executor.execute(()->{
            try{
                ShizukuFileAccess.RestoreResult result=ShizukuFileAccess.restoreBackup(item.path);
                runOnUiThread(()->{
                    if(result.hadConflict()){
                        backupNotice=tr(
                            "Save \""+result.restoredName+"\" was restored.\n\nA file with the same name already existed in pt_saves. The existing file was preserved in Backup as:\n"+new File(result.conflictBackupPath).getName()+"\n\nNo save was overwritten.",
                            "Сохранение «"+result.restoredName+"» восстановлено.\n\nВ pt_saves уже был файл с таким именем. Существующий файл сохранён в Backup под именем:\n"+new File(result.conflictBackupPath).getName()+"\n\nДанные не потеряны."
                        );
                    }else backupNotice=tr(
                        "Save \""+result.restoredName+"\" was restored from Backup.",
                        "Сохранение «"+result.restoredName+"» восстановлено из Backup."
                    );
                    showBackups();
                });
            }catch(Throwable t){
                AppLog.exception(this,"BACKUP_RESTORE","Restore failed",t);
                runOnUiThread(()->show(tr(
                    "Could not restore save: ","Не удалось восстановить сохранение: "
                )+t.getMessage()+tr("\nThe backup is kept if verification fails.","\nПри ошибке проверки резервная копия остаётся на месте.")));
            }
        });
    }
    private void scanSaves(){
        if(homeMode!=1)return;
        if(!hasPermission()){
            LinearLayout list=pageContents[0];
            list.removeAllViews();
            addHeader(tr("Save Editor","Редактор сохранений"),"SaveTurquoiseIcon.png");
            Button back=actionButton(tr("Back","Назад"),()->{homeMode=0;setPage(0);});
            back.setBackground(background(TEAL,Color.TRANSPARENT,dp(25)));
            list.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));
            addEmptyCard(tr("Shizuku permission required","Требуется разрешение Shizuku"),
                tr("Connect Shizuku to load your worlds. Your saves have not been changed.","Подключи Shizuku, чтобы загрузить список миров. Сохранения не изменялись."),list);
            Button connect=actionButton(tr("Connect Shizuku","Подключить Shizuku"),this::requestShizuku);
            LinearLayout.LayoutParams connectParams=new LinearLayout.LayoutParams(-1,dp(46));
            connectParams.topMargin=dp(10);
            list.addView(connect,connectParams);
            refreshStatus();
            return;
        }
        showingBackups=false;
        LinearLayout loadingList=pageContents[0];
        loadingList.removeAllViews();
        addHeader(tr("Your worlds","Твои миры"),"SaveTurquoiseIcon.png");
        Button loadingBack=actionButton(tr("Back","Назад"),()->{homeMode=0;setPage(0);});
        loadingBack.setBackground(background(TEAL,Color.TRANSPARENT,dp(25)));
        loadingList.addView(loadingBack,new LinearLayout.LayoutParams(-1,dp(44)));
        addEmptyCard(tr("Loading worlds…","Загружаю список миров…"),
            tr("Reading save files. This may take a moment.","Читаю файлы сохранений. Это может занять немного времени."),loadingList);
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
                    for(String line:listing.split("\\r?\n")){
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
            }catch(Throwable t){
                AppLog.exception(this,"SAVE_SCAN","Save scan failed",t);
                runOnUiThread(()->{
                    if(homeMode!=1)return;
                    LinearLayout list=pageContents[0];
                    list.removeAllViews();
                    addHeader(tr("Your worlds","Твои миры"),"SaveTurquoiseIcon.png");
                    Button back=actionButton(tr("Back","Назад"),()->{homeMode=0;setPage(0);});
                    back.setBackground(background(TEAL,Color.TRANSPARENT,dp(25)));
                    list.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));
                    addEmptyCard(tr("Could not load worlds","Не удалось загрузить миры"),
                        String.valueOf(t.getMessage()),list);
                    Button retry=actionButton(tr("Try again","Повторить"),this::scanSaves);
                    LinearLayout.LayoutParams retryParams=new LinearLayout.LayoutParams(-1,dp(46));
                    retryParams.topMargin=dp(10);
                    list.addView(retry,retryParams);
                });
            }
        });
    }
    private void renderSaves(List<SaveSlot>found){
        if(homeMode!=1)return;
        LinearLayout content=pageContents[0];
        content.removeAllViews();
        addHeader(tr("Your worlds","Твои миры"),"SaveTurquoiseIcon.png");
        Button back=actionButton(tr("Back","Назад"),()->{homeMode=0;setPage(0);});
        back.setBackground(background(TEAL,Color.TRANSPARENT,dp(25)));
        LinearLayout.LayoutParams backParams=new LinearLayout.LayoutParams(-1,dp(44));
        backParams.bottomMargin=dp(10);
        content.addView(back,backParams);
        TextView hint=text(tr("Choose a world to edit its save.","Выбери мир, чтобы изменить сохранение."),13,mutedColor(),false);hint.setPadding(0,dp(4),0,dp(12));content.addView(hint);
        if(found.isEmpty()){addEmptyCard(tr("No worlds found","Миры не найдены"),tr("Make sure the game has created a save.","Проверь, что игра уже создала сохранение."),content);show(tr("Worlds found: 0","Найдено миров: 0"));return;}
        for(SaveSlot slot:found){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(background(cardColor(),borderColor(),dp(26)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);content.addView(card,cp);
            LinearLayout titleRow=new LinearLayout(this);titleRow.setOrientation(LinearLayout.HORIZONTAL);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(assetIconTile("DocumentsPinkIcon.png",30),new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView worldName=text(slot.displayName,16,inkColor(),true);worldName.setPadding(dp(10),0,0,0);
            titleRow.addView(worldName,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(titleRow);
            TextView detail=text(slot.fileName+"  •  "+slot.sizeText,12,mutedColor(),false);detail.setPadding(0,dp(3),0,dp(8));card.addView(detail);
            Button editSave=actionButton(tr("Edit save","Редактировать"),()->confirmBeginEditing(slot));
            editSave.setBackground(background(PINK,Color.TRANSPARENT,dp(28)));
            card.addView(editSave,new LinearLayout.LayoutParams(-1,dp(46)));
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
        card.setBackground(background(cardColor(),borderColor(),dp(18)));
        card.addView(text(title,17,inkColor(),true));TextView d=text(description,13,mutedColor(),false);d.setPadding(0,dp(5),0,0);card.addView(d);
        parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }
    private void confirmBeginEditing(SaveSlot slot){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(8),dp(18),dp(18));
        card.setBackground(background(cardColor(),borderColor(),dp(28)));
        View pinkBar=new View(this);
        pinkBar.setBackground(background(PINK,Color.TRANSPARENT,dp(8)));
        card.addView(pinkBar,new LinearLayout.LayoutParams(-1,dp(6)));
        TextView title=text(tr("Create a backup?","Создать резервную копию?"),22,inkColor(),true);
        title.setPadding(0,dp(16),0,dp(8));
        card.addView(title);
        TextView message=text(tr(
            "Before editing, you can save an untouched copy of this world. The original save will stay in the game folder either way.",
            "Перед редактированием можно сохранить нетронутую копию этого мира. Исходное сохранение в любом случае останется в папке игры."
        ),14,mutedColor(),false);
        message.setPadding(0,0,0,dp(14));
        card.addView(message);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(card).create();
        Button create=actionButton(tr("Create backup and edit","Создать копию и редактировать"),()->{
            dialog.dismiss();startEditing(slot,true);
        });
        create.setBackground(background(PINK,Color.TRANSPARENT,dp(26)));
        Button skip=actionButton(tr("Edit without backup","Редактировать без копии"),()->{
            dialog.dismiss();startEditing(slot,false);
        });
        skip.setBackground(background(TEAL,Color.TRANSPARENT,dp(26)));
        Button cancel=actionButton(tr("Cancel","Отмена"),dialog::dismiss);
        cancel.setTextColor(darkTheme?Color.WHITE:inkColor());
        cancel.setBackground(darkTheme?background(TEAL,Color.TRANSPARENT,dp(26)):background(Color.rgb(245,240,232),borderColor(),dp(26)));
        for(Button button:new Button[]{create,skip,cancel}){
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(44));
            params.topMargin=dp(7);card.addView(button,params);
        }
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params=dialog.getWindow().getAttributes();
            params.dimAmount=0.45f;dialog.getWindow().setAttributes(params);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(32),-2);
        }
    }
    private void startEditing(SaveSlot slot,boolean createBackup){
        if(!hasPermission()){show(tr("Shizuku permission is required.","Нужно разрешение Shizuku."));requestShizuku();return;}
        show(tr("Preparing save…","Подготавливаю сохранение…"));
        executor.execute(()->{
            try{
                String path=slot.fileName.startsWith("/")?slot.fileName:ShizukuFileAccess.SAVE_DIR+"/"+slot.fileName;
                ShizukuFileAccess.Stage stage=ShizukuFileAccess.stageForEditing(this,path,createBackup);
                if(stage.backup!=null)AppLog.i(this,"SAVE_EDIT","Original backup created "+stage.backup);
                else AppLog.i(this,"SAVE_EDIT","Editing started without an optional backup");
                runOnUiThread(()->{
                    Intent intent=new Intent(this,SaveEditorActivity.class);
                    intent.putExtra("stage_path",stage.file.getAbsolutePath());
                    intent.putExtra("target_path",stage.target);
                    if(stage.backup!=null)intent.putExtra("backup_path",stage.backup);
                    startActivity(intent);
                });
            }catch(Throwable t){AppLog.exception(this,"SAVE_EDIT","Could not stage save",t);runOnUiThread(()->show(tr("Could not prepare save: ","Не удалось подготовить сохранение: ")+t.getMessage()));}
        });
    }
    private void showLogs(){
        String logs=AppLog.read(this);
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(8),dp(18),dp(18));
        card.setBackground(background(cardColor(),borderColor(),dp(26)));
        View pinkBar=new View(this);
        pinkBar.setBackground(background(PINK,Color.TRANSPARENT,dp(8)));
        card.addView(pinkBar,new LinearLayout.LayoutParams(-1,dp(6)));
        TextView title=text("Журнал приложения",20,inkColor(),true);
        title.setPadding(0,dp(14),0,dp(10));
        card.addView(title);
        ScrollView scroll=new ScrollView(this);
        TextView body=text(logs.length()==0?"Журнал пуст.":logs,12,mutedColor(),false);
        body.setTextIsSelectable(true);
        body.setPadding(dp(10),dp(10),dp(10),dp(10));
        body.setBackground(background(Color.rgb(250,246,238),borderColor(),dp(14)));
        scroll.addView(body);
        card.addView(scroll,new LinearLayout.LayoutParams(-1,dp(320)));
        Button close=actionButton("Закрыть",()->{});
        close.setBackground(background(TEAL,Color.TRANSPARENT,dp(28)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));
        p.topMargin=dp(12);
        card.addView(close,p);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(card).create();
        close.setOnClickListener(v->dialog.dismiss());
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params=dialog.getWindow().getAttributes();
            params.dimAmount=0.45f;
            dialog.getWindow().setAttributes(params);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(32),-2);
        }
    }
    private void shareLogs(){
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,AppLog.read(this));
        startActivity(Intent.createChooser(i,"Поделиться журналом"));
    }
    @Override public void onBackPressed(){
        if(selectedPage==0&&homeMode!=0){homeMode=0;setPage(0);return;}
        super.onBackPressed();
    }

    @Override protected void onDestroy(){
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        executor.shutdownNow();super.onDestroy();
    }
}