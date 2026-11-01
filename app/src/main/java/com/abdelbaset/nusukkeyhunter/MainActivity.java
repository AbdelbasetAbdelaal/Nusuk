package com.abdelbaset.nusukkeyhunter;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.net.Uri;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import android.content.pm.PackageManager;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    EditText codeInput, commentsInput;
    TextView countText, listText, currentCodeText;
    ArrayList<String> codes = new ArrayList<>();
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    int accepted=0, rejected=0, pending=0;
    String currentCode = "";
    LinearLayout root;
    final String DATA="codes", ACCEPT="accepted", REJECT="rejected";
    final Pattern invitePattern = Pattern.compile("(?iu)NG[\\s\\p{Z}\\p{Cf}]*[-_\\p{Pd}:：][\\s\\p{Z}\\p{Cf}]*([A-Z0-9][A-Z0-9_-]{2,}[A-Z0-9])");

    final String[] sourceLinks = {
        "https://www.facebook.com/share/g/1FaRSgvxMT/",
        "https://www.facebook.com/share/g/19emnymdLB/",
        "https://www.facebook.com/share/1DEPUwaNLk/",
        "https://www.facebook.com/share/1DoMzCRQqN/"
    };

    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF123D35);
        getWindow().setNavigationBarColor(0xFF123D35);
        load();
        render();
        handleSharedIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedIntent(intent);
    }

    TextView text(String s,int size) {
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(0xFF18352F);
        t.setPadding(0,8,0,8);
        return t;
    }

    Button button(String s) {
        Button b=new Button(this); b.setText(s); return b;
    }

    void render() {
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(0xFFF4F7F5);
        sc.addView(root);
        setContentView(sc);

        TextView title=text("Nusuk Key Hunter",26);
        title.setTypeface(null,Typeface.BOLD);
        title.setTextColor(0xFF123D35);
        root.addView(title);
        root.addView(text("منظّم أكواد الدعوة — بحث مساعد في فيسبوك واستخراج الأكواد من المنشورات المشتركة",14));

        countText=text("",16); root.addView(countText); updateCounts();

        root.addView(text("مساعد نسك — تجربة الأكواد بالترتيب",20));
        currentCodeText=text("",18);
        currentCodeText.setTypeface(null,Typeface.BOLD);
        root.addView(currentCodeText);
        Button copyOpen=button("نسخ الكود التالي وفتح تطبيق نسك");
        root.addView(copyOpen);
        copyOpen.setOnClickListener(v->copyNextAndOpenNusuk());
        Button next=button("نسخ الكود التالي فقط");
        root.addView(next);
        next.setOnClickListener(v->copyNext());
        Button openNusuk=button("فتح تطبيق نسك");
        root.addView(openNusuk);
        openNusuk.setOnClickListener(v->launchNusukApp());

        root.addView(text("بعد تجربة الكود في نسك، سجّل النتيجة:",16));
        LinearLayout resultRow=new LinearLayout(this); root.addView(resultRow);
        Button ok=button("تم قبوله");
        resultRow.addView(ok,new LinearLayout.LayoutParams(0,-2,1));
        Button bad=button("مرفوض / مستخدم");
        resultRow.addView(bad,new LinearLayout.LayoutParams(0,-2,1));
        ok.setOnClickListener(v->mark(true));
        bad.setOnClickListener(v->mark(false));

        root.addView(text("البحث عن الأكواد في فيسبوك",20));
        Button search=button("فتح بحث فيسبوك عن أكواد نسك");
        root.addView(search);
        search.setOnClickListener(v -> openUrl("https://www.facebook.com/search/posts/?q=كود%20نسك"));

        root.addView(text("المجموعات والمنشورات التي أرسلت روابطها",16));
        String[] labels={"فتح مجموعة فيسبوك الأولى","فتح مجموعة فيسبوك الثانية","فتح منشور فيسبوك الثالث","فتح منشور فيسبوك الرابع"};
        for(int i=0;i<sourceLinks.length;i++) {
            final String link=sourceLinks[i];
            Button b=button(labels[i]);
            root.addView(b);
            b.setOnClickListener(v -> openUrl(link));
        }

        root.addView(text("استخراج الأكواد من التعليقات",20));
        root.addView(text("افتح المنشور في فيسبوك، واضغط مطولًا على نص التعليق وحدد النص، ثم افتح قائمة «المزيد» واختر Nusuk Key Hunter إن ظهر. أو انسخ نص التعليقات والصقه في المربع التالي.",14));
        commentsInput=new EditText(this);
        commentsInput.setHint("الصق هنا نص تعليق أو مجموعة تعليقات...");
        commentsInput.setMinLines(4);
        commentsInput.setGravity(Gravity.TOP);
        root.addView(commentsInput);
        Button scanClipboard=button("فحص النص المنسوخ في الحافظة عن أكواد NG");
        root.addView(scanClipboard);
        scanClipboard.setOnClickListener(v->scanClipboardForCodes());

        Button scanComments=button("فحص التعليقات واستخراج الأكواد");
        root.addView(scanComments);
        scanComments.setOnClickListener(v->{
            String raw=commentsInput.getText().toString();
            int before=codes.size();
            addCodes(raw);
            commentsInput.setText("");
            if(codes.size()==before) Toast.makeText(this,"لم يتم العثور على كود دعوة جديد في النص",Toast.LENGTH_LONG).show();
        });

        root.addView(text("أضف الأكواد أو نص المنشور يدويًا",18));
        codeInput=new EditText(this);
        codeInput.setHint("الصق كودًا أو نص منشور يحتوي على كود NG-");
        codeInput.setMinLines(3);
        codeInput.setGravity(Gravity.TOP);
        root.addView(codeInput);

        Button add=button("استخراج الأكواد وإزالة المكرر");
        root.addView(add);
        add.setOnClickListener(v->{ addCodes(codeInput.getText().toString()); codeInput.setText(""); });



        Button export=button("نسخ جميع الأكواد"); root.addView(export);
        export.setOnClickListener(v->{
            StringBuilder s=new StringBuilder();
            for(String c:codes) s.append(c).append("\n");
            copy(s.toString());
            Toast.makeText(this,"تم نسخ قائمة الأكواد",Toast.LENGTH_SHORT).show();
        });

        Button clear=button("مسح جميع البيانات"); root.addView(clear);
        clear.setOnClickListener(v->new AlertDialog.Builder(this)
            .setMessage("هل تريد مسح الأكواد والنتائج؟")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("مسح",(d,w)->{
                codes.clear(); unique.clear(); accepted=0; rejected=0; pending=0; currentCode="";
                getPreferences(0).edit().clear().apply();
                save(); render();
            }).show());

        listText=text("",14); root.addView(listText); updateCounts();
    }

    void openUrl(String url) {
        try {
            Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch(Exception e) {
            Toast.makeText(this,"تعذر فتح الرابط. تأكد من وجود متصفح أو تطبيق فيسبوك.",Toast.LENGTH_LONG).show();
        }
    }

    void handleSharedIntent(Intent intent) {
        if(intent==null) return;
        String action=intent.getAction();
        String shared="";
        if(Intent.ACTION_SEND.equals(action)) {
            shared=intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if(Intent.ACTION_PROCESS_TEXT.equals(action)) {
            CharSequence selected=intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if(selected!=null) shared=selected.toString();
        } else {
            return;
        }
        if(shared==null) shared="";
        LinkedHashSet<String> found=extractInviteCodes(shared);
        if(!found.isEmpty()) {
            addFoundCodes(found);
            Toast.makeText(this,"استخرج التطبيق "+found.size()+" كود من المنشور المشترك",Toast.LENGTH_LONG).show();
        } else {
            if(codeInput!=null) codeInput.setText(shared);
            Toast.makeText(this,"لم يظهر كود بصيغة NG- في النص المشترك. راجع النص أو انسخ الكود يدويًا.",Toast.LENGTH_LONG).show();
        }
    }

    LinkedHashSet<String> extractInviteCodes(String raw) {
        LinkedHashSet<String> found=new LinkedHashSet<>();
        if(raw==null || raw.trim().isEmpty()) return found;

        // Normalize full-width Latin letters and dash variants that may appear in copied text.
        String normalized=raw
            .replace('Ｎ','N').replace('Ｇ','G').replace('ｎ','n').replace('ｇ','g')
            .replace('‐','-').replace('‑','-').replace('‒','-')
            .replace('–','-').replace('—','-').replace('−','-')
            .replace('﹣','-').replace('－','-');

        Matcher m=invitePattern.matcher(normalized);
        while(m.find()) {
            String suffix=m.group(1).replaceAll("[\\s\\p{Z}\\p{Cf}]","");
            if(suffix.length()>=4) found.add("NG-"+suffix);
        }
        return found;
    }

    void addCodes(String raw) {
        if(raw==null || raw.trim().isEmpty()) {
            Toast.makeText(this,"لا يوجد نص للفحص. انسخ التعليق الذي يحتوي على الكود أو الصقه أولًا.",Toast.LENGTH_LONG).show();
            return;
        }
        LinkedHashSet<String> found=extractInviteCodes(raw);
        int added=addFoundCodes(found);
        if(found.isEmpty()) {
            Toast.makeText(this,"تم فحص النص ("+raw.length()+" حرفًا) ولم يظهر كود يبدأ بـ NG-. تأكد أن النص المنسوخ يتضمن الرمز كاملًا.",Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this,"وجدت "+found.size()+" كود يبدأ بـ NG-، وأُضيف "+added+" كود جديد",Toast.LENGTH_LONG).show();
        }
    }

    int addFoundCodes(Collection<String> found) {
        int added=0;
        for(String c:found) if(unique.add(c)) { codes.add(c); added++; }
        save();
        updateCounts();
        return added;
    }

    void scanClipboardForCodes() {
        try {
            ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(cm==null || !cm.hasPrimaryClip()) {
                Toast.makeText(this,"الحافظة فارغة. انسخ نص التعليق من فيسبوك أولًا.",Toast.LENGTH_LONG).show();
                return;
            }
            ClipData clip=cm.getPrimaryClip();
            StringBuilder raw=new StringBuilder();
            for(int i=0;i<clip.getItemCount();i++) {
                CharSequence item=clip.getItemAt(i).coerceToText(this);
                if(item!=null) raw.append(item).append("\n");
            }
            if(commentsInput!=null) commentsInput.setText(raw.toString());
            addCodes(raw.toString());
        } catch(Exception e) {
            Toast.makeText(this,"تعذر قراءة الحافظة. الصق نص التعليقات في المربع المخصص.",Toast.LENGTH_LONG).show();
        }
    }

    void copyNext() {
        for(String c:codes) {
            if(!getPreferences(0).getBoolean("done_"+c,false)) {
                currentCode=c;
                copy(c);
                Toast.makeText(this,"تم نسخ "+c+" — انتقل إلى نسك وأدخله يدويًا",Toast.LENGTH_LONG).show();
                return;
            }
        }
        Toast.makeText(this,"لا توجد أكواد معلّقة",Toast.LENGTH_SHORT).show();
    }

    void mark(boolean good) {
        if(currentCode.isEmpty() || getPreferences(0).getBoolean("done_"+currentCode,false)) {
            Toast.makeText(this,"اضغط «نسخ الكود التالي» أولًا",Toast.LENGTH_LONG).show();
            return;
        }
        getPreferences(0).edit().putBoolean("done_"+currentCode,true).apply();
        if(good) accepted++; else rejected++;
        if(pending>0) pending--;
        save();
        updateCounts();
        Toast.makeText(this,"تم تسجيل نتيجة "+currentCode,Toast.LENGTH_SHORT).show();
        currentCode="";
    }

    void copy(String s) {
        ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
            .setPrimaryClip(ClipData.newPlainText("Nusuk codes",s));
    }

    void updateCounts() {
        if(countText!=null)
            countText.setText("إجمالي الأكواد: "+codes.size()+"   |   معلّقة: "+pending+"   |   مقبولة: "+accepted+"   |   مرفوضة: "+rejected);
        if(listText!=null) {
            StringBuilder s=new StringBuilder("قائمة الأكواد (آخر 100):\n");
            int start=Math.max(0,codes.size()-100);
            for(int i=start;i<codes.size();i++) s.append(i+1).append(". ").append(codes.get(i)).append("\n");
            listText.setText(s.toString());
        }
    }

    void save() {
        StringBuilder s=new StringBuilder();
        for(String c:codes) s.append(c).append("\n");
        pending=Math.max(0,codes.size()-accepted-rejected);
        getPreferences(0).edit().putString(DATA,s.toString()).putInt(ACCEPT,accepted).putInt(REJECT,rejected).apply();
    }

    void load() {
        String raw=getPreferences(0).getString(DATA,"");
        for(String c:raw.split("\n")) if(!c.trim().isEmpty() && unique.add(c.trim())) codes.add(c.trim());
        accepted=getPreferences(0).getInt(ACCEPT,0);
        rejected=getPreferences(0).getInt(REJECT,0);
        pending=Math.max(0,codes.size()-accepted-rejected);
    }
}
