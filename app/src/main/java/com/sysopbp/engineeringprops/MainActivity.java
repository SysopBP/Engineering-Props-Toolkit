package com.sysopbp.engineeringprops;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
  LinearLayout page, navigation;
  TextView output, heading;
  final int BG=0xFF09090D, CARD=0xFF211F29, ACCENT=0xFF914D70;
  String run(String cmd) {
    try {
      Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();
      ByteArrayOutputStream out=new ByteArrayOutputStream();
      byte[] buffer=new byte[4096]; int n;
      while((n=p.getInputStream().read(buffer))!=-1 && out.size()<180000) out.write(buffer,0,n);
      int status=p.waitFor();
      return out.toString("UTF-8")+(status==0?"":"\nExit status: "+status);
    } catch(Exception e){return "Root command unavailable: "+e.getMessage();}
  }
  GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);d.setStroke(1,0xFF583849);return d;}
  TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(22,16,22,16);return t;}
  void button(String title,String command){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(bg(CARD,30));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,5,0,5);page.addView(b,lp);b.setOnClickListener(v->execute(title,command));}
  void execute(String title,String command){output.setText(title+" — loading…");new Thread(()->{String result=run(command);runOnUiThread(()->output.setText(result));}).start();}
  String properties(){return "for k in ro.product.model ro.build.version.release ro.build.version.sdk ro.build.display.id ro.build.type ro.build.tags ro.debuggable ro.secure ro.adb.secure ro.boot.verifiedbootstate ro.boot.vbmeta.device_state ro.boot.flash.locked ro.boot.warranty_bit; do echo \"$k=$(getprop $k)\"; done; echo SELINUX=$(getenforce); echo ROOT=$(id)";}
  String adb(){return "echo '[ADB daemon]'; for k in init.svc.adbd service.adb.root ro.debuggable ro.secure ro.adb.secure persist.sys.usb.config; do echo \"$k=$(getprop $k)\"; done; echo '[Process identity]'; for p in $(pidof adbd); do grep -E 'Name:|Uid:|Gid:' /proc/$p/status; readlink /proc/$p/exe; done; echo '[Note] UID 2000 is shell, UID 0 is root. This does not attempt adb root.'";}
  String partition(){return "for k in ro.build ro.bootimage.build ro.system.build ro.system_ext.build ro.system_dlkm.build ro.vendor.build ro.vendor_dlkm.build ro.product.build ro.odm.build; do echo \"$k.type=$(getprop $k.type)  tags=$(getprop $k.tags)\"; done; echo '[On-disk baseline]'; grep -hE '^ro\\.(build\\.type|debuggable|secure|adb\\.secure)=' /system/build.prop /vendor/build.prop /product/build.prop 2>/dev/null";}
  String modules(){return "for m in /data/adb/modules/*; do [ -d \"$m\" ] || continue; echo \"=== ${m##*/} ===\"; [ -e \"$m/disable\" ] && echo DISABLED || echo ENABLED; grep -E '^(name|version)=' \"$m/module.prop\" 2>/dev/null; done; echo '[Engineering property writers]'; grep -nE 'resetprop|ro.build.type|ro.debuggable|ro.secure|ro.adb.secure' /data/adb/modules/enable_eng/*.sh /data/adb/modules/engineering_props_toolkit/*.sh 2>/dev/null | head -100";}
  void screen(String title){heading.setText(title);page.removeAllViews();page.addView(heading);page.addView(output);output.setText("Select a diagnostic. Read-only unless explicitly labeled.");switch(title){
    case "Dashboard":
      button("Refresh engineering status",properties());
      button("ADB daemon & root capability",adb());
      button("Partition identities",partition());break;
    case "Modules":
      button("KernelSU module inventory",modules());
      button("Toolkit configuration","cat /data/adb/modules/engineering_props_toolkit/config.conf 2>/dev/null || echo 'Toolkit not found'");
      button("Check module v5 logs","ls -lh /data/adb/modules/engineering_props_toolkit/logs/ 2>/dev/null");
      break;
    case "Samsung":
      button("Samsung firmware & CSC","for k in ro.product.model ro.boot.bootloader ro.csc.sales_code ril.sales_code ro.boot.warranty_bit ro.boot.verifiedbootstate ro.boot.flash.locked; do echo \"$k=$(getprop $k)\"; done");
      button("SELinux audit","getenforce; logcat -d -b all -t 500 2>/dev/null | grep -i 'avc: denied' | tail -35");
      button("IMS service inventory","pm list packages | grep -iE 'ims|rcs|telephony' | head -60");
      break;
    case "Boot":
      button("Boot timeline","tail -90 /data/adb/modules/engineering_props_toolkit/logs/boot-timeline.txt 2>/dev/null");
      button("Boot events","tail -100 /data/adb/modules/engineering_props_toolkit/logs/boot.log 2>/dev/null");
      button("Recent crashes","logcat -d -b crash -t 180 2>/dev/null");
      button("Export diagnostic snapshot","OUT=/data/media/0/Download/EngineeringProps_App_Diagnostic.txt; { date; "+properties()+"; "+adb()+"; "+partition()+"; "+modules()+"; } > \"$OUT\" 2>&1; chmod 644 \"$OUT\"; echo \"Saved $OUT\"");
      break;
    case "Graphics":
      button("Enable rendering profile",toggle("GRAPHICS_PROFILE","1"));
      button("Disable rendering profile",toggle("GRAPHICS_PROFILE","0"));
      button("Enable GPU overdraw",toggle("GRAPHICS_OVERDRAW","1"));
      button("Disable GPU overdraw",toggle("GRAPHICS_OVERDRAW","0"));
      page.addView(text("Changes apply when the module service next runs or after reboot. No automatic ADB restart, SELinux change, or fingerprint rewriting.",12));break;
  }}
  String toggle(String key,String val){return "f=/data/adb/modules/engineering_props_toolkit/config.conf; if [ -f \"$f\" ]; then sed -i 's/^"+key+"=.*/"+key+"="+val+"/' \"$f\"; grep '^"+key+"=' \"$f\"; else echo 'Toolkit config missing'; fi";}
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);
    ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setPadding(22,16,22,20);page.setOrientation(1);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    navigation=new LinearLayout(this);navigation.setPadding(8,8,8,12);navigation.setGravity(Gravity.CENTER);navigation.setBackground(bg(0xFF17151C,38));
    for(String name:new String[]{"Dashboard","Modules","Samsung","Boot","Graphics"}){TextView tab=text(name,11);tab.setGravity(Gravity.CENTER);tab.setPadding(7,18,7,18);navigation.addView(tab,new LinearLayout.LayoutParams(0,-2,1));tab.setOnClickListener(v->screen(name));}
    LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,-2);navParams.setMargins(12,0,12,12);root.addView(navigation,navParams);
    setContentView(root);heading=text("Engineering Props • v5",23);heading.setTypeface(null,Typeface.BOLD);output=text("",13);output.setBackground(bg(CARD,28));screen("Dashboard");
  }
}
