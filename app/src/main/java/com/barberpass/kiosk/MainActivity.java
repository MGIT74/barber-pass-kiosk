package com.barberpass.kiosk;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.text.InputType;
import android.net.Uri;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

public class MainActivity extends Activity {
  private static final String DEFAULT_URL="https://app.thebarberone.com/kiosk.html?salon=the-barber-one-oyonnax";
  private WebView web;
  private TextView status;
  private FrameLayout root;
  private boolean loaded=false;
  private boolean chromeOpened=false;
  private boolean pageError=false;
  private boolean kioskLockEnabled=false;
  private SharedPreferences prefs;
  private int taps=0;
  private long lastTap=0;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs=getSharedPreferences("kiosk",MODE_PRIVATE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    immersive();
    root=new FrameLayout(this);
    root.setBackgroundColor(Color.WHITE);
    web=new WebView(this);
    root.addView(web,new FrameLayout.LayoutParams(-1,-1));
    status=new TextView(this);
    status.setTextColor(Color.BLACK);
    status.setBackgroundColor(0xFFF4F4F4);
    status.setPadding(24,24,24,24);
    status.setTextSize(17);
    status.setText("Chargement de Barber Pass...");
    status.setGravity(Gravity.CENTER);
    root.addView(status,new FrameLayout.LayoutParams(-1,-1));
    status.setOnClickListener(v->showTroubleshooting());
    View adminHotspot=new View(this);
    FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(
      (int)(72*getResources().getDisplayMetrics().density),
      (int)(72*getResources().getDisplayMetrics().density),
      Gravity.TOP|Gravity.START);
    root.addView(adminHotspot,hp);
    TextView exitBubble=new TextView(this);
    exitBubble.setText("⏻");
    exitBubble.setTextSize(25);
    exitBubble.setGravity(Gravity.CENTER);
    exitBubble.setTextColor(Color.WHITE);
    android.graphics.drawable.GradientDrawable bubbleBg=new android.graphics.drawable.GradientDrawable();
    bubbleBg.setColor(0xCC222222);
    bubbleBg.setCornerRadius(1000);
    exitBubble.setBackground(bubbleBg);
    int bubbleSize=(int)(52*getResources().getDisplayMetrics().density);
    int margin=(int)(16*getResources().getDisplayMetrics().density);
    FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(bubbleSize,bubbleSize,Gravity.BOTTOM|Gravity.RIGHT);
    ep.setMargins(margin,margin,margin,margin);
    root.addView(exitBubble,ep);
    exitBubble.setContentDescription("Sortir de la borne avec le code administrateur");
    final Handler exitHandler=new Handler(Looper.getMainLooper());
    final Runnable exitAction=()->requestExit();
    exitBubble.setOnTouchListener(new View.OnTouchListener(){
      private float downX,downY;
      private boolean pending=false;
      @Override public boolean onTouch(View v,android.view.MotionEvent event){
        switch(event.getActionMasked()){
          case android.view.MotionEvent.ACTION_DOWN:
            downX=event.getX();downY=event.getY();
            pending=true;
            exitHandler.postDelayed(exitAction,3000);
            return true;
          case android.view.MotionEvent.ACTION_MOVE:
            if(Math.abs(event.getX()-downX)>24*getResources().getDisplayMetrics().density
              ||Math.abs(event.getY()-downY)>24*getResources().getDisplayMetrics().density){
              pending=false;exitHandler.removeCallbacks(exitAction);
            }
            return true;
          case android.view.MotionEvent.ACTION_UP:
          case android.view.MotionEvent.ACTION_CANCEL:
            pending=false;exitHandler.removeCallbacks(exitAction);
            return true;
          default:return true;
        }
      }
    });
    adminHotspot.setOnClickListener(v->{
      long now=System.currentTimeMillis();
      taps=now-lastTap<1600?taps+1:1;
      lastTap=now;
      if(taps>=6){taps=0;adminLogin();}
    });
    setContentView(root);
    kioskLockEnabled=prefs.getBoolean("kiosk_lock",false);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setSupportMultipleWindows(false);
    s.setJavaScriptCanOpenWindowsAutomatically(false);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);
    s.setMediaPlaybackRequiresUserGesture(false);
    web.setLayerType(View.LAYER_TYPE_HARDWARE,null);
    CookieManager.getInstance().setAcceptCookie(true);
    web.setWebViewClient(new WebViewClient(){
      @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){loaded=false;pageError=false;status.setVisibility(View.VISIBLE);status.setText("Chargement de Barber Pass...\\n\\nTouchez ici si la page ne s’affiche pas.");}
      @Override public void onPageFinished(WebView v,String url){loaded=true;if(!pageError)status.setVisibility(View.GONE);}
      @Override public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error){
        if(request.isForMainFrame()) showError("Erreur de connexion : " + error.getDescription());
      }
      @Override public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response){
        if(request.isForMainFrame()) showError("Erreur HTTP " + response.getStatusCode());
      }
      @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();showError("Erreur de certificat HTTPS : " + error.toString());}

      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req){
        return !isAllowed(req.getUrl().toString());
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onConsoleMessage(ConsoleMessage m){
        if(m.messageLevel()==ConsoleMessage.MessageLevel.ERROR) runOnUiThread(()->{
          if(status.getVisibility()!=View.VISIBLE){status.setText("Erreur JavaScript : " + m.message() + "\\n\\nTouchez ici pour les options.");status.setVisibility(View.VISIBLE);}
        });
        return true;
      }
    });
    if(!prefs.contains("url")) configure(false); else load();
    if(kioskLockEnabled)root.postDelayed(()->startKioskLock(),1200);
  }

  private boolean isDeviceOwner(){
    DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
    return dpm!=null && dpm.isDeviceOwnerApp(getPackageName());
  }
  private void requestExit(){
    if(!prefs.contains("pin")){setPin();return;}
    EditText input=new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    new AlertDialog.Builder(this).setTitle("Sortir de Barber Pass")
      .setMessage("Saisissez le code PIN administrateur pour quitter la borne.")
      .setView(input).setNegativeButton("Annuler",null)
      .setPositiveButton("Déverrouiller",(d,w)->{
        if(input.getText().toString().equals(prefs.getString("pin",""))){
          kioskLockEnabled=false;
          prefs.edit().putBoolean("kiosk_lock",false).apply();
          stopKioskLock();
          finish();
        }else Toast.makeText(this,"Code incorrect",Toast.LENGTH_SHORT).show();
      }).show();
  }
  private void startKioskLock(){
    if(Build.VERSION.SDK_INT<21)return;
    try {
      if(!prefs.contains("pin")){kioskLockEnabled=false;prefs.edit().putBoolean("kiosk_lock",false).apply();setPin();return;}
      DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
      if(isDeviceOwner())dpm.setLockTaskPackages(new ComponentName(this,KioskAdminReceiver.class),new String[]{getPackageName()});
      ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
      if(am.getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_NONE)startLockTask();
    }catch(Exception e){Toast.makeText(this,"Verrouillage indisponible : "+e.getMessage(),Toast.LENGTH_LONG).show();}
  }
  private void stopKioskLock(){
    try{stopLockTask();}catch(Exception ignored){}
  }
  private void showError(String message){pageError=true;runOnUiThread(()->{status.setText(message+"\\n\\nTouchez ici pour les options.");status.setVisibility(View.VISIBLE);});}
  private void showTroubleshooting(){
    String version="inconnue";
    try { android.content.pm.PackageInfo p=getPackageManager().getPackageInfo("com.google.android.webview",0);if(p!=null)version=p.versionName; } catch(Exception ignored){}
    final String webviewVersion=version;
    new AlertDialog.Builder(this).setTitle("Diagnostic Barber Pass")
      .setMessage("Version Android : "+Build.VERSION.RELEASE+"\\nWebView : "+webviewVersion+"\\n\\nSi la borne reste blanche, mettez à jour Android System WebView ou Chrome, ou essayez le navigateur.")
      .setPositiveButton("Ouvrir dans Chrome",(d,w)->{openChrome();})
      .setNeutralButton("Réessayer",(d,w)->load())
      .setNegativeButton("Fermer",null).show();
  }
  private boolean isAllowed(String url){
    Uri u=Uri.parse(url);
    return "https".equalsIgnoreCase(u.getScheme())
      && "app.thebarberone.com".equalsIgnoreCase(u.getHost());
  }

  private void immersive(){
    getWindow().getDecorView().setSystemUiVisibility(5894|1024|512|4096);
  }
  @Override public void onWindowFocusChanged(boolean focused){
    super.onWindowFocusChanged(focused);
    if(focused)immersive();
  }
  private void load(){
    String url=prefs.getString("url",DEFAULT_URL);
    if(!isAllowed(url))return;
    status.setVisibility(View.VISIBLE);
    status.setText("Chargement de la borne plein écran...\\n\\nSi la page reste blanche, touchez ici pour ouvrir Chrome.");
    pageError=false;
    web.loadUrl(url);
    new Handler(Looper.getMainLooper()).postDelayed(()->{
      if(!loaded && !pageError)showError("Le navigateur intégré ne répond pas. Essayez Chrome.");
    },12000);
  }
  private void openChrome(){
    try {
      Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(prefs.getString("url",DEFAULT_URL)));
      intent.setPackage("com.android.chrome");
      startActivity(intent);
    }catch(Exception e){showError("Impossible d'ouvrir Chrome : "+e.getMessage());}
  }

  private void configure(boolean cancelable){
    LinearLayout layout=new LinearLayout(this);
    layout.setPadding(36,20,36,10);
    layout.setOrientation(LinearLayout.VERTICAL);
    TextView info=new TextView(this);
    info.setText("Adresse de la borne du salon (HTTPS, domaine app.thebarberone.com)");
    layout.addView(info);
    EditText url=new EditText(this);
    url.setSingleLine(false);
    url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
    url.setText(prefs.getString("url",DEFAULT_URL));
    layout.addView(url);
    AlertDialog dialog=new AlertDialog.Builder(this)
      .setTitle("Configurer le salon").setView(layout).setCancelable(cancelable)
      .setPositiveButton("Enregistrer",null).create();
    dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
      String val=url.getText().toString().trim();
      Uri uri=Uri.parse(val);
      if(!isAllowed(val)||!"/kiosk.html".equals(uri.getPath())
          ||uri.getQueryParameter("salon")==null
          ||uri.getQueryParameter("salon").trim().isEmpty()){
        url.setError("URL invalide : https://app.thebarberone.com/kiosk.html?salon=...");
        return;
      }
      prefs.edit().putString("url",val).apply();
      dialog.dismiss();
      load();
    }));
    dialog.show();
  }

  private void adminLogin(){
    if(!prefs.contains("pin")){setPin();return;}
    EditText input=new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    new AlertDialog.Builder(this).setTitle("Code administrateur").setView(input)
      .setNegativeButton("Annuler",null)
      .setPositiveButton("Valider",(d,w)->{
        if(input.getText().toString().equals(prefs.getString("pin","")))adminMenu();
        else Toast.makeText(this,"Code incorrect",Toast.LENGTH_SHORT).show();
      }).show();
  }
  private void setPin(){
    EditText input=new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    input.setHint("6 chiffres minimum");
    new AlertDialog.Builder(this).setTitle("Créer le code administrateur")
      .setView(input).setCancelable(false)
      .setPositiveButton("Enregistrer",(d,w)->{
        String p=input.getText().toString();
        if(p.matches("[0-9]{6,}")){
          prefs.edit().putString("pin",p).apply();
          adminMenu();
        } else setPin();
      }).show();
  }
  private void adminMenu(){
    String[] actions={"Changer de salon","Actualiser la borne","Ouvrir dans Chrome","Changer le code PIN",kioskLockEnabled?"Désactiver le verrouillage":"Activer le verrouillage écran","Quitter le mode kiosque","Statut du verrouillage"};
    new AlertDialog.Builder(this).setTitle("Administration Barber Pass")
      .setItems(actions,(d,which)->{
        if(which==0)configure(true);
        if(which==1)load();
        if(which==2)openChrome();
        if(which==3){prefs.edit().remove("pin").apply();setPin();}
        if(which==4){
          kioskLockEnabled=!kioskLockEnabled;
          prefs.edit().putBoolean("kiosk_lock",kioskLockEnabled).apply();
          if(kioskLockEnabled)new AlertDialog.Builder(this).setTitle("Verrouillage Android")
            .setMessage(isDeviceOwner()?"Le mode administrateur Android est actif. Les boutons Home et Applications récentes seront bloqués.":"Activez l’épinglage des fenêtres dans les paramètres de sécurité Android. Sans mode administrateur, les touches système permettent encore de désépingler.")
            .setPositiveButton("Activer",(dd,ww)->startKioskLock())
            .setNegativeButton("Annuler",(dd,ww)->{kioskLockEnabled=false;prefs.edit().putBoolean("kiosk_lock",false).apply();}).show();
          else stopKioskLock();
        }
        if(which==5){stopKioskLock();kioskLockEnabled=false;prefs.edit().putBoolean("kiosk_lock",false).apply();finish();}
        if(which==6)new AlertDialog.Builder(this).setTitle("Protection de la tablette")
          .setMessage(isDeviceOwner()?"Mode administrateur Android actif : Home, Retour et Applications récentes sont verrouillables.":"Mode administrateur Android non activé. Le simple épinglage peut être désactivé par les touches système. Une configuration Device Owner séparée est nécessaire pour bloquer Home et Applications récentes.")
          .setPositiveButton("OK",null).show();
      }).setNegativeButton("Fermer",null).show();
  }
  @Override public void onBackPressed(){ /* prevent accidental web navigation */ }
  @Override protected void onDestroy(){
    if(web!=null)web.destroy();
    super.onDestroy();
  }
}
