package com.abdelbaset.nusukkeyhunter;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText codeInput;
    TextView countText, listText;
    ArrayList<String> codes = new ArrayList<>();
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    int accepted=0, rejected=0, pending=0;
    LinearLayout root;
    final String PREF="nusuk_keys", DATA="codes", ACCEPT="accepted", REJECT="rejected";

    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF123D35);
        getWindow().setNavigationBarColor(0xFF123D35);
        load(); render();
    }
    TextView text(String s,int size) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(0xFF18352F); t.setPadding(0,8,0,8); return t;
    }
    Button button(String s) {
        Button b=new Button(this); b.setText(s); return b;
    }
    void render() {
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(1); root.setPadding(24,24,24,24); root.setBackgroundColor(0xFFF4F7F5);
        sc.addView(root); setContentView(sc);
        TextView title=text("Nusuk Key Hunter",26); title.setTypeface(null,Typeface.BOLD); title.setTextColor(0xFF123D35); root.addView(title);
        root.addView(text("منظّم أكواد الدعوة — حفظ، إزالة التكرار، ومتابعة النتائج",14));
        countText=text("",16); root.addView(countText); updateCounts();
        root.addView(text("أضف كودًا أو الصق مجموعة أكواد",18));
        codeInput=new EditText(this); codeInput.setHint("الصق الأكواد هنا، كل كود في سطر"); codeInput.setMinLines(3); codeInput.setGravity(Gravity.TOP); root.addView(codeInput);
        Button add=button("إضافة الأكواد وإزالة المكرر"); root.addView(add);
        add.setOnClickListener(v->{ addCodes(codeInput.getText().toString()); codeInput.setText(""); });
        Button next=button("نسخ الكود التالي"); root.addView(next);
        next.setOnClickListener(v->copyNext());
        Button open=button("فتح موقع نسك"); root.addView(open);
        open.setOnClickListener(v->{ try { startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://services.nusuk.sa/"))); } catch(Exception e){} });
        root.addView(text("سجّل نتيجة الكود المحدد",18));
        LinearLayout row=new LinearLayout(this); root.addView(row);
        Button ok=button("تم قبوله"); row.addView(ok,new LinearLayout.LayoutParams(0,-2,1));
        Button bad=button("مرفوض / مستخدم"); row.addView(bad,new LinearLayout.LayoutParams(0,-2,1));
        ok.setOnClickListener(v->mark(true)); bad.setOnClickListener(v->mark(false));
        Button export=button("نسخ جميع الأكواد"); root.addView(export);
        export.setOnClickListener(v->{ StringBuilder s=new StringBuilder(); for(String c:codes)s.append(c).append("\n"); copy(s.toString()); });
        Button clear=button("مسح جميع البيانات"); root.addView(clear);
        clear.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("هل تريد مسح الأكواد والنتائج؟").setNegativeButton("إلغاء",null).setPositiveButton("مسح",(d,w)->{codes.clear();unique.clear();accepted=0;rejected=0;pending=0;save();render();}).show());
        listText=text("",14); root.addView(listText); updateCounts();
    }
    void addCodes(String raw) {
        String[] parts=raw.split("[\\s,;]+"); int added=0;
        for(String p:parts){ String c=p.trim(); if(c.length()>=5 && unique.add(c)){codes.add(c);pending++;added++;} }
        save(); updateCounts(); Toast.makeText(this,"تمت إضافة "+added+" كود جديد",Toast.LENGTH_LONG).show();
    }
    void copyNext() {
        for(String c:codes) {
            String key="state_"+c;
            if(!getPreferences(0).contains(key)) { copy(c); Toast.makeText(this,"تم نسخ الكود. انتقل إلى نسك وأدخله يدويًا",Toast.LENGTH_LONG).show(); return; }
        }
        Toast.makeText(this,"لا توجد أكواد معلّقة",Toast.LENGTH_SHORT).show();
    }
    void mark(boolean good) {
        String current="";
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm!=null && cm.hasPrimaryClip()) try { current=cm.getPrimaryClip().getItemAt(0).coerceToText(this).toString(); } catch(Exception e){}
        if(current.isEmpty() || !unique.contains(current)){ Toast.makeText(this,"انسخ الكود من زر «نسخ الكود التالي» أولًا",Toast.LENGTH_LONG).show(); return; }
        getPreferences(0).edit().putBoolean("state_"+current,true).apply();
        if(good)accepted++;else rejected++; if(pending>0)pending--;
        save(); updateCounts();
    }
    void copy(String s) { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Nusuk codes",s)); }
    void updateCounts() {
        if(countText!=null)countText.setText("إجمالي الأكواد: "+codes.size()+"   |   معلّقة: "+pending+"   |   مقبولة: "+accepted+"   |   مرفوضة: "+rejected);
        if(listText!=null){StringBuilder s=new StringBuilder("قائمة الأكواد (آخر 100):\n");int start=Math.max(0,codes.size()-100);for(int i=start;i<codes.size();i++)s.append(i+1).append(". ").append(codes.get(i)).append("\n");listText.setText(s.toString());}
    }
    void save(){StringBuilder s=new StringBuilder();for(String c:codes)s.append(c).append("\n");getPreferences(0).edit().putString(DATA,s.toString()).putInt(ACCEPT,accepted).putInt(REJECT,rejected).apply();pending=codes.size()-accepted-rejected;}
    void load(){String raw=getPreferences(0).getString(DATA,"");for(String c:raw.split("\\n"))if(!c.trim().isEmpty()&&unique.add(c.trim()))codes.add(c.trim());accepted=getPreferences(0).getInt(ACCEPT,0);rejected=getPreferences(0).getInt(REJECT,0);pending=Math.max(0,codes.size()-accepted-rejected);}
    void markStatus(String c, boolean good) { }
}
