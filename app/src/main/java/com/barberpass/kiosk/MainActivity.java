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

public class MainActivity extends Activity {
  private static final String DEFAULT_URL="https://app.thebarberone.com/kiosk.html?salon=the-barber-one-oyonnax";
  private WebView web;
  private SharedPreferences prefs;
  private int taps=0;
  private long lastTap=0;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs=getSharedPreferences("kiosk",MODE_PRIVATE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    immersive();
    FrameLayout root=new FrameLayout(this);
    root.setBackgroundColor(Color.WHITE);
    web=new WebView(this);
    root.addView(web,new FrameLayout.LayoutParams(-1,-1));
    View adminHotspot=new View(this);
    FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(
      (int)(72*getResources().getDisplayMetrics().density),
      (int)(72*getResources().getDisplayMetrics().density),
      Gravity.TOP|Gravity.START);
    root.addView(adminHotspot,hp);
    adminHotspot.setOnClickListener(v->{
      long now=System.currentTimeMillis();
      taps=now-lastTap<1600?taps+1:1;
      lastTap=now;
      if(taps>=6){taps=0;adminLogin();}
    });
    setContentView(root);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setSupportMultipleWindows(false);
    CookieManager.getInstance().setAcceptCookie(true);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req){
        return !isAllowed(req.getUrl().toString());
      }
    });
    web.setWebChromeClient(new WebChromeClient());
    if(!prefs.contains("url")) configure(false); else load();
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
    if(isAllowed(url))web.loadUrl(url);
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
    String[] actions={"Changer de salon","Actualiser la borne","Changer le code PIN"};
    new AlertDialog.Builder(this).setTitle("Administration Barber Pass")
      .setItems(actions,(d,which)->{
        if(which==0)configure(true);
        if(which==1)web.reload();
        if(which==2){prefs.edit().remove("pin").apply();setPin();}
      }).setNegativeButton("Fermer",null).show();
  }
  @Override public void onBackPressed(){ /* prevent accidental web navigation */ }
  @Override protected void onDestroy(){
    if(web!=null)web.destroy();
    super.onDestroy();
  }
}
