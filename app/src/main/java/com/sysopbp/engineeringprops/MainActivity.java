package com.sysopbp.engineeringprops;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.io.*;
public class MainActivity extends Activity {
 LinearLayout page; TextView output;
 String exec(String cmd) { try { Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start(); ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] a=new byte[4096]; int n; while((n=p.getInputStream().read(a))!=-1 && b.size()<100000)b.write(a,0,n); p.waitFor(); return b.toString("UTF-8"); }catch(Exception e){return "Root unavailable: "+e.getMessage();} }
 GradientDrawable card(){GradientDrawable d=new GradientDrawable();d.setColor(0xFF1D1D24);d.setCornerRadius(32);d.setStroke(2,0xFF653B56);return d;}
 TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(20,16,20,16);return t;}
 void addButton(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setBackground(card());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,7,0,7);page.addView(b,lp);b.setOnClickListener(v->new Thread(r).start());}
 void show(String s){runOnUiThread(()->output.setText(s));}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xFF09090D);getWindow().setNavigationBarColor(0xFF09090D);ScrollView sc=new ScrollView(this);sc.setBackgroundColor(0xFF09090D);page=new LinearLayout(this);page.setPadding(30,30,30,40);page.setOrientation(1);sc.addView(page);setContentView(sc);
 TextView title=label("Engineering Props  •  LAB",25);title.setTypeface(null,Typeface.BOLD);page.addView(title);page.addView(label("One UI-inspired developer console · KernelSU",14));
 output=label("Tap Refresh to inspect your device.",13);output.setBackground(card());page.addView(output);
 addButton("Refresh live properties",()->{StringBuilder sb=new StringBuilder();for(String k:new String[]{"ro.build.type","ro.debuggable","ro.secure","ro.adb.secure","ro.build.version.sdk","ro.product.model","ro.boot.verifiedbootstate","init.svc.adbd","sys.boot_completed","debug.hwui.profile","debug.hwui.overdraw"})sb.append(k).append(" = ").append(exec("getprop "+k).trim()).append("\n");show(sb.toString());});
 addButton("Module status",()->show(exec("ls -la /data/adb/modules | head -60")));
 addButton("Show toolkit configuration",()->show(exec("cat /data/adb/modules/*engineering*/config.conf 2>/dev/null || echo 'Toolkit module not found'")));
 addButton("Enable rendering profile",()->toggle("GRAPHICS_PROFILE","1"));
 addButton("Disable rendering profile",()->toggle("GRAPHICS_PROFILE","0"));
 addButton("Enable GPU overdraw",()->toggle("GRAPHICS_OVERDRAW","1"));
 addButton("Disable GPU overdraw",()->toggle("GRAPHICS_OVERDRAW","0"));
 addButton("View boot report",()->show(exec("cat /data/adb/modules/*engineering*/logs/boot.log 2>/dev/null | tail -60")));
 page.addView(label("Configuration changes take effect after the module service runs or device reboots. No ADB authentication or lock enforcement changes.",12));
 }
 void toggle(String key,String value){String cmd="for f in /data/adb/modules/*engineering*/config.conf; do [ -f \"$f\" ] || continue; sed -i 's/^"+key+"=.*/"+key+"="+value+"/' \"$f\"; echo updated:$f; done";show(exec(cmd));}
}
