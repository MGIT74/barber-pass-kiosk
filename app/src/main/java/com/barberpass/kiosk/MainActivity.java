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
import android.graphics.Point;
import org.json.JSONArray;
import org.json.JSONObject;

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
  private static final String SCREENS_KEY="screens_v1";
  private TextView exitBubble;
  private boolean bubbleMoved=false;
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
    exitBubble=new TextView(this);
    exitBubble.setText("\u2699\uFE0E"); // Force monochrome text glyph, not green emoji
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
    final Handler bubbleHandler=new Handler(Looper.getMainLooper());
    exitBubble.setOnTouchListener(new View.OnTouchListener(){
      float startRawX,startRawY,startX,startY;
      boolean longPressed=false,dragged=false;
      Runnable action;
      @Override public boolean onTouch(View v,android.view.MotionEvent e){
        switch(e.getActionMasked()){
          case MotionEvent.ACTION_DOWN:
            startRawX=e.getRawX();startRawY=e.getRawY();
            startX=v.getX();startY=v.getY();
            dragged=false;longPressed=false;
            action=()->{if(!dragged){longPressed=true;adminLogin();}};
            bubbleHandler.postDelayed(action,3000);
            return true;
          case MotionEvent.ACTION_MOVE:
            float dx=e.getRawX()-startRawX,dy=e.getRawY()-startRawY;
            if(Math.abs(dx)>12*getResources().getDisplayMetrics().density
              ||Math.abs(dy)>12*getResources().getDisplayMetrics().density){
              dragged=true;bubbleHandler.removeCallbacks(action);
            }
            if(dragged){
              v.setX(Math.max(0,Math.min(root.getWidth()-v.getWidth(),startX+dx)));
              v.setY(Math.max(0,Math.min(root.getHeight()-v.getHeight(),startY+dy)));
            }
            return true;
          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            bubbleHandler.removeCallbacks(action);
            if(dragged){
              prefs.edit().putFloat("bubble_x",v.getX()/Math.max(1,root.getWidth()-v.getWidth()))
                .putFloat("bubble_y",v.getY()/Math.max(1,root.getHeight()-v.getHeight())).apply();
            }
            return true;
          default:return true;
        }
      }
    });
    exitBubble.post(()->{
      if(prefs.contains("bubble_x")&&prefs.contains("bubble_y")){
        exitBubble.setX(Math.max(0,Math.min(root.getWidth()-exitBubble.getWidth(),
          prefs.getFloat("bubble_x",1f)*(root.getWidth()-exitBubble.getWidth()))));
        exitBubble.setY(Math.max(0,Math.min(root.getHeight()-exitBubble.getHeight(),
          prefs.getFloat("bubble_y",1f)*(root.getHeight()-exitBubble.getHeight()))));
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
    if(!validScreenUrl(url))return false;
    if("app.thebarberone.com".equalsIgnoreCase(u.getHost()))return true;
    JSONArray arr=screens();
    for(int i=0;i<arr.length();i++){
      JSONObject obj=arr.optJSONObject(i);
      if(obj!=null && url.equals(obj.optString("url","")))return true;
    }
    return false;
  }

  private void immersive(){
    getWindow().getDecorView().setSystemUiVisibility(5894|1024|512|4096);
  }
  @Override public void onWindowFocusChanged(boolean focused){
    super.onWindowFocusChanged(focused);
    if(focused)immersive();
  }
  private void load(){
    String url=prefs.getString("active_url",prefs.getString("url",DEFAULT_URL));
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
      Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(prefs.getString("active_url",prefs.getString("url",DEFAULT_URL))));
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
    String[] actions={"Choisir un écran","Gérer les liens","Changer de salon","Actualiser la borne","Ouvrir dans Chrome","Changer le code PIN",kioskLockEnabled?"Désactiver le verrouillage":"Activer le verrouillage écran","Quitter le mode kiosque","Statut du verrouillage"};
    new AlertDialog.Builder(this).setTitle("Administration Barber Pass")
      .setItems(actions,(d,which)->{
        if(which==0)chooseScreen();
        if(which==1)manageScreens();
        if(which==2)configure(true);
        if(which==3)load();
        if(which==4)openChrome();
        if(which==5){prefs.edit().remove("pin").apply();setPin();}
        if(which==6){
          kioskLockEnabled=!kioskLockEnabled;
          prefs.edit().putBoolean("kiosk_lock",kioskLockEnabled).apply();
          if(kioskLockEnabled)new AlertDialog.Builder(this).setTitle("Verrouillage Android")
            .setMessage(isDeviceOwner()?"Le mode administrateur Android est actif. Les boutons Home et Applications récentes seront bloqués.":"Activez l’épinglage des fenêtres dans les paramètres de sécurité Android. Sans mode administrateur, les touches système permettent encore de désépingler.")
            .setPositiveButton("Activer",(dd,ww)->startKioskLock())
            .setNegativeButton("Annuler",(dd,ww)->{kioskLockEnabled=false;prefs.edit().putBoolean("kiosk_lock",false).apply();}).show();
          else stopKioskLock();
        }
        if(which==7){stopKioskLock();kioskLockEnabled=false;prefs.edit().putBoolean("kiosk_lock",false).apply();finish();}
        if(which==8)new AlertDialog.Builder(this).setTitle("Protection de la tablette")
          .setMessage(isDeviceOwner()?"Mode administrateur Android actif : Home, Retour et Applications récentes sont verrouillables.":"Mode administrateur Android non activé. Le simple épinglage peut être désactivé par les touches système. Une configuration Device Owner séparée est nécessaire pour bloquer Home et Applications récentes.")
          .setPositiveButton("OK",null).show();
      }).setNegativeButton("Fermer",null).show();
  }

  private JSONArray screens(){
    try{
      if(prefs.contains(SCREENS_KEY))return new JSONArray(prefs.getString(SCREENS_KEY,"[]"));
    }catch(Exception ignored){}
    JSONArray arr=new JSONArray();
    JSONObject initial=new JSONObject();
    try{
      initial.put("name","Prise de rendez-vous");
      initial.put("url",prefs.getString("url",DEFAULT_URL));
      arr.put(initial);
    }catch(Exception ignored){}
    return arr;
  }
  private void saveScreens(JSONArray arr){prefs.edit().putString(SCREENS_KEY,arr.toString()).apply();}
  private String screenName(JSONObject obj){return obj.optString("name","Écran");}
  private void chooseScreen(){
    JSONArray arr=screens();
    if(arr.length()==0){manageScreens();return;}
    String[] names=new String[arr.length()];
    for(int i=0;i<arr.length();i++)names[i]=screenName(arr.optJSONObject(i));
    new AlertDialog.Builder(this).setTitle("Afficher un écran")
      .setItems(names,(d,i)->{
        JSONObject obj=arr.optJSONObject(i);
        if(obj==null)return;
        String url=obj.optString("url","");
        if(!validScreenUrl(url)){Toast.makeText(this,"Adresse non autorisée",Toast.LENGTH_LONG).show();return;}
        prefs.edit().putString("active_url",url).apply();
        load();
      }).setNeutralButton("Gérer les liens",(d,w)->manageScreens())
      .setNegativeButton("Annuler",null).show();
  }
  private boolean validScreenUrl(String value){
    try{
      Uri u=Uri.parse(value);
      return "https".equalsIgnoreCase(u.getScheme())
        && u.getHost()!=null && !u.getHost().trim().isEmpty()
        && u.getUserInfo()==null;
    }catch(Exception ignored){return false;}
  }
  private void manageScreens(){
    JSONArray arr=screens();
    String[] names=new String[arr.length()+1];
    for(int i=0;i<arr.length();i++)names[i]=screenName(arr.optJSONObject(i));
    names[arr.length()]="+ Ajouter un lien";
    new AlertDialog.Builder(this).setTitle("Gérer les écrans")
      .setItems(names,(d,i)->{
        if(i==arr.length())editScreen(-1);
        else screenActions(i);
      }).setNegativeButton("Fermer",null).show();
  }
  private void screenActions(int index){
    new AlertDialog.Builder(this).setTitle(screenName(screens().optJSONObject(index)))
      .setItems(new String[]{"Afficher","Modifier","Supprimer"},(d,choice)->{
        if(choice==0){
          JSONObject obj=screens().optJSONObject(index);
          if(obj!=null){
            prefs.edit().putString("active_url",obj.optString("url",DEFAULT_URL)).apply();
            load();
          }
        }else if(choice==1)editScreen(index);
        else new AlertDialog.Builder(this).setTitle("Supprimer ce lien ?")
          .setMessage("Cette action ne supprime pas le site internet.")
          .setPositiveButton("Supprimer",(dd,w)->{
            JSONArray old=screens(),updated=new JSONArray();
            for(int i=0;i<old.length();i++)if(i!=index)updated.put(old.optJSONObject(i));
            saveScreens(updated);manageScreens();
          }).setNegativeButton("Annuler",null).show();
      }).setNegativeButton("Retour",(d,w)->manageScreens()).show();
  }
  private void editScreen(int index){
    JSONObject current=index>=0?screens().optJSONObject(index):null;
    LinearLayout form=new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    int pad=(int)(20*getResources().getDisplayMetrics().density);
    form.setPadding(pad,pad,pad,pad);
    EditText name=new EditText(this);
    name.setSingleLine(true);name.setHint("Nom de l'écran");
    name.setText(current==null?"":current.optString("name",""));
    form.addView(name);
    EditText url=new EditText(this);
    url.setSingleLine(true);url.setHint("https://...");
    url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
    url.setText(current==null?"":current.optString("url",""));
    form.addView(url);
    AlertDialog dialog=new AlertDialog.Builder(this)
      .setTitle(index<0?"Ajouter un lien":"Modifier le lien").setView(form)
      .setPositiveButton("Enregistrer",null).setNegativeButton("Annuler",null).create();
    dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
      String label=name.getText().toString().trim(),link=url.getText().toString().trim();
      if(label.isEmpty()){name.setError("Nom obligatoire");return;}
      if(!validScreenUrl(link)){url.setError("Une adresse HTTPS valide est nécessaire");return;}
      if(!"app.thebarberone.com".equalsIgnoreCase(Uri.parse(link).getHost())){
        new AlertDialog.Builder(this).setTitle("Domaine externe")
          .setMessage("Autoriser l'affichage de "+Uri.parse(link).getHost()+" dans la borne ? N'ajoutez que des sites de confiance.")
          .setPositiveButton("Autoriser",(dd,w)->saveScreen(index,label,link,dialog))
          .setNegativeButton("Annuler",null).show();
      }else saveScreen(index,label,link,dialog);
    }));
    dialog.show();
  }
  private void saveScreen(int index,String name,String url,AlertDialog dialog){
    JSONArray old=screens(),updated=new JSONArray();
    try{
      for(int i=0;i<old.length();i++){
        if(i==index){JSONObject obj=new JSONObject();obj.put("name",name);obj.put("url",url);updated.put(obj);}
        else updated.put(old.optJSONObject(i));
      }
      if(index<0){JSONObject obj=new JSONObject();obj.put("name",name);obj.put("url",url);updated.put(obj);}
      saveScreens(updated);
      dialog.dismiss();manageScreens();
    }catch(Exception e){Toast.makeText(this,"Impossible d'enregistrer",Toast.LENGTH_LONG).show();}
  }
  @Override public void onBackPressed(){ /* prevent accidental web navigation */ }
  @Override protected void onDestroy(){
    if(web!=null)web.destroy();
    super.onDestroy();
  }
}
