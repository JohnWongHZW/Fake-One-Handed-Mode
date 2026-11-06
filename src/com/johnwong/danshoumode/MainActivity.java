package com.johnwong.danshoumode;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;

public final class MainActivity extends Activity {
    private int bg,surface,ink,sub,accent,onAccent,container;
    private boolean resumed,pending,left,dirty;
    private String queuedOperation;
    private int percent=60;
    private String diagnostics="等待系统进程回复…";
    private TextView status,pill,logView;
    private Button toggle,leftButton,rightButton,customButton;
    private final Button[] sizes=new Button[3];
    private final int[] presets={60,70,80};
    private Preview preview;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private final Runnable poll=new Runnable(){public void run(){if(resumed){query("status");handler.postDelayed(this,1200);}}};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        bg=c(dark?"#101713":"#F5FBF4");surface=c(dark?"#1D2B22":"#E8F0E6");ink=c(dark?"#E3F0E5":"#162C20");
        sub=c(dark?"#B4C8B9":"#526B5B");accent=c(dark?"#97D7AE":"#256B47");onAccent=c(dark?"#08391F":"#FFFFFF");container=c(dark?"#2C4938":"#D4EDDA");
        preferences=getSharedPreferences("hand_ui",MODE_PRIVATE);left=preferences.getBoolean("left",false);
        percent=preferences.getInt("percent",60);dirty=preferences.contains("percent")&&preferences.getBoolean("dirty",false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        LinearLayout page=vertical();page.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return i;});
        LinearLayout header=row();ImageView icon=new ImageView(this);icon.setImageResource(res("hand_icon","drawable"));icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(shape(container,20));icon.setClipToOutline(true);header.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView title=label("伪单手模式",28,ink);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setPadding(dp(12),0,0,0);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));pill=label("连接中",12,accent);pill.setPadding(dp(10),dp(7),dp(10),dp(7));pill.setBackground(shape(container,30));header.addView(pill);page.addView(header);space(page,20);

        LinearLayout hero=card(container,32,18);hero.addView(label("完整界面，单手可及",22,ink));space(hero,8);
        preview=new Preview(this);preview.setContentDescription("单手模式示意，可在下方选择左右手和缩放百分比");hero.addView(preview,new LinearLayout.LayoutParams(-1,dp(220)));
        TextView hint=label("双击空白处任意位置退出",13,sub);hint.setGravity(Gravity.CENTER);hero.addView(hint);page.addView(hero);space(page,18);

        LinearLayout hand=row();leftButton=button("左手",surface,ink,()->choose(true));rightButton=button("右手",surface,ink,()->choose(false));
        addPair(hand,leftButton,rightButton,52);page.addView(hand);space(page,16);
        page.addView(label("缩放比例",16,sub));space(page,8);LinearLayout sizeRow=row();
        for(int n=0;n<presets.length;n++){final int value=presets[n];sizes[n]=button(value+"%",surface,ink,()->{percent=value;changed();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);if(n<2)lp.rightMargin=dp(8);sizeRow.addView(sizes[n],lp);}page.addView(sizeRow);
        space(page,8);customButton=button("自定义比例",surface,ink,this::customize);page.addView(customButton,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView scaleHint=label("宽高等比缩放 · 修改后即时生效",12,sub);scaleHint.setPadding(dp(2),dp(8),dp(2),dp(12));page.addView(scaleHint);
        toggle=button("开启单手模式",accent,onAccent,()->query("hand_toggle"));toggle.setTextSize(18);toggle.setEnabled(false);page.addView(toggle,new LinearLayout.LayoutParams(-1,dp(64)));
        status=label("正在连接模块…",13,sub);status.setPadding(dp(6),dp(10),dp(6),dp(14));page.addView(status);
        page.addView(button("添加到控制中心",surface,ink,this::addTile),new LinearLayout.LayoutParams(-1,dp(52)));space(page,18);

        LinearLayout settings=card(surface,26,16);settings.addView(link("查看日志","连接、运行状态与故障诊断",this::showLogs,true));
        space(settings,8);settings.addView(link("使用教程","双击空白处退出 · 连按音量减应急恢复",this::showHelp,true));
        space(settings,8);settings.addView(link("关于","作者：流浪本子商人",()->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/JohnWongHZW")));}
            catch(ActivityNotFoundException e){Toast.makeText(this,"请安装可打开网页的应用",Toast.LENGTH_LONG).show();}
        },true));
        LinearLayout hideRow=row();TextView hideLabel=label("隐藏桌面图标",16,ink);hideRow.addView(hideLabel,new LinearLayout.LayoutParams(0,dp(52),1));
        Switch hide=new Switch(this);hide.setContentDescription("隐藏桌面图标，之后从 LSP 模块设置打开");
        ComponentName alias=new ComponentName(this,Protocol.PACKAGE+".LauncherAlias");hide.setChecked(getPackageManager().getComponentEnabledSetting(alias)==PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        hide.setThumbTintList(ColorStateList.valueOf(accent));hide.setTrackTintList(ColorStateList.valueOf(container));
        hide.setOnCheckedChangeListener((b,on)->{try{getPackageManager().setComponentEnabledSetting(alias,on?PackageManager.COMPONENT_ENABLED_STATE_DISABLED:PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);Toast.makeText(this,on?"已隐藏，可从 LSP 模块设置重新打开":"已恢复桌面图标",Toast.LENGTH_SHORT).show();}catch(RuntimeException e){Toast.makeText(this,"图标设置失败",Toast.LENGTH_SHORT).show();}});
        hideRow.addView(hide);settings.addView(hideRow);page.addView(settings);space(page,22);

        LinearLayout extra=row();extra.setPadding(dp(16),dp(12),dp(16),dp(12));extra.setBackground(shape(surface,24));
        ImageView oldIcon=new ImageView(this);oldIcon.setImageResource(res("portrait","drawable"));oldIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);oldIcon.setBackground(shape(container,14));oldIcon.setClipToOutline(true);extra.addView(oldIcon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout caption=vertical();caption.setPadding(dp(12),0,0,0);caption.addView(label("卢本伟模式",17,ink));caption.addView(label("（作者的妙妙小功能）",12,sub));extra.addView(caption,new LinearLayout.LayoutParams(0,-2,1));extra.addView(label("›",26,sub));extra.setOnClickListener(v->startActivity(new Intent(this,LegacyActivity.class)));page.addView(extra);
        space(page,18);TextView footer=label("1.2.0 预览版 · libxposed API 102",12,sub);footer.setGravity(Gravity.CENTER);page.addView(footer);
        setContentView(scroll);getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);renderSelection();
    }
    private void choose(boolean handLeft){left=handLeft;changed();}
    private void changed(){dirty=true;persist();renderSelection();query("save_hand");}
    private void persist(){preferences.edit().putBoolean("left",left).putInt("percent",percent).putBoolean("dirty",dirty).apply();}
    private void renderSelection(){
        tint(leftButton,left?accent:surface,left?onAccent:ink);tint(rightButton,!left?accent:surface,!left?onAccent:ink);
        boolean preset=false;for(int i=0;i<sizes.length;i++){tint(sizes[i],percent==presets[i]?container:surface,ink);preset|=percent==presets[i];}
        customButton.setText(preset?"自定义比例":"自定义比例 · "+percent+"%");tint(customButton,preset?surface:container,ink);preview.invalidate();
    }
    private void customize(){
        EditText value=new EditText(this);value.setText(Integer.toString(percent));value.setInputType(InputType.TYPE_CLASS_NUMBER);value.setSelectAllOnFocus(true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("自定义比例").setMessage("输入 40–85 的整数，单位为百分比。宽和高按同一比例缩放，无需校准屏幕尺寸，可在单手模式下直接调整。").setView(value).setNegativeButton("取消",null).setPositiveButton("应用",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int n=Integer.parseInt(value.getText().toString().trim());Geometry.percentScale(n);percent=n;changed();d.dismiss();}catch(RuntimeException e){value.setError("请输入 40–85 之间的整数");}}));d.show();
    }
    private void query(String op){
        if(pending){if(!"status".equals(op))queuedOperation=op;return;}
        boolean saving=dirty&&!"stop".equals(op)&&!"clear_logs".equals(op);
        pending=true;Intent request=new Intent().putExtra("op",saving?"save_hand":op);
        boolean handChoice=left;int sizeChoice=percent;
        if(saving)request.putExtra("side",left?"left":"right").putExtra("percent",percent);
        AppBridge.request(this,request,b->{pending=false;if(isFinishing()||isDestroyed())return;
            boolean connected=b.getBoolean("connected");
            if(saving&&connected&&b.getBoolean("configSaved")&&left==handChoice&&percent==sizeChoice){dirty=false;persist();}
            if(connected&&!dirty){left="left".equals(b.getString("side"));percent=b.getInt("percent",60);persist();renderSelection();}
            boolean active=b.getBoolean("handActive"),available=connected&&b.getBoolean("handReady");
            toggle.setEnabled(available);toggle.setAlpha(available?1:.55f);toggle.setText(active?"退出单手模式":"开启单手模式");
            pill.setText(active?"已开启":available?"已就绪":connected?"需适配":"未连接");status.setText(b.getString("message",""));
            diagnostics=b.getString("diagnostics","尚未连接到系统进程，请确认已启用本模块并重启。")+"\n"+b.getString("runtime","")+"\n"+b.getString("message","");
            if(logView!=null)logView.setText(diagnostics);
            if("clear_logs".equals(op))Toast.makeText(this,b.getBoolean("logsCleared")?
                    "运行记录已清理，设备信息和当前状态保留":"清理失败，模块未连接",Toast.LENGTH_SHORT).show();
            String next=queuedOperation;queuedOperation=null;
            if(next==null&&saving&&op.equals("hand_toggle"))next="hand_toggle";
            if(next!=null&&connected&&(!saving||b.getBoolean("configSaved")))query(next);
        });
    }
    private void addTile(){
        StatusBarManager manager=getSystemService(StatusBarManager.class);
        try{manager.requestAddTileService(new ComponentName(this,HandTileService.class),"伪单手模式",Icon.createWithResource(this,res("ic_tile_hand","drawable")),getMainExecutor(),result->Toast.makeText(this,"可在控制中心编辑页面找到「伪单手模式」",Toast.LENGTH_LONG).show());}
        catch(RuntimeException e){Toast.makeText(this,"请打开控制中心 → 编辑，手动添加伪单手模式",Toast.LENGTH_LONG).show();}
    }
    private void showLogs(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(bg);TextView text=label(diagnostics,12,ink);text.setTypeface(Typeface.MONOSPACE);text.setTextIsSelectable(true);text.setPadding(dp(20),dp(12),dp(20),dp(20));scroll.addView(text);
        logView=text;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("模块日志").setView(scroll)
                .setNegativeButton("关闭",null).setNeutralButton("清理日志",null)
                .setPositiveButton("复制",(d,w)->getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("伪单手模式日志",diagnostics))).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->query("clear_logs")));
        dialog.setOnDismissListener(d->{if(logView==text)logView=null;});dialog.show();
    }
    private void showHelp(){
        new AlertDialog.Builder(this).setTitle("使用教程").setMessage(
                "开启单手模式\n在 LSPosed 勾选系统框架、系统界面和小米桌面，重启后使用。竖屏时选择左手或右手、设置缩放比例，再点「开启单手模式」。完整界面会等比缩到对应的下角。\n\n"+
                "比例与快捷开关\n提供 60%、70%、80% 预设，也可输入 40%–85% 的自定义比例，无需屏幕英寸或校准。运行中调整比例和左右手会直接生效；配置自动保存，控制中心开关使用已保存的配置。\n\n"+
                "小白条手势\n解锁并使用手势导航时，从底部左半侧或右半侧向下滑，进入对应下角。开启后，从小窗底部向上滑即可退出。\n\n"+
                "退出与恢复\n双击小窗外空白处，或再次点首页按钮、控制中心开关即可退出。单手模式熄屏时退出，单次最多运行 2 小时，重启后默认关闭。\n\n"+
                "操作异常时\n2 秒内连按 3 次音量减，或点击下方「退出并恢复」。若手机完全无响应，请强制重启；仍有问题可在首页查看并复制日志。\n\n"+
                "隐藏桌面图标\n隐藏后仍可从 LSP 的模块设置打开应用，快捷开关也可继续使用。")
                .setNegativeButton("关闭",null).setPositiveButton("退出并恢复",(d,w)->query("stop")).show();
    }
    private int c(String s){return Color.parseColor(s);}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}private int res(String n,String type){return getResources().getIdentifier(n,type,getPackageName());}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private LinearLayout card(int color,int r,int p){LinearLayout l=vertical();l.setPadding(dp(p),dp(p),dp(p),dp(p));l.setBackground(shape(color,r));return l;}
    private TextView label(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private void space(LinearLayout l,int d){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(d)));}
    private void tint(Button b,int fill,int color){b.setTextColor(color);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2255AA77),shape(fill,26),null));}
    private Button button(String s,int fill,int color,Runnable run){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setPadding(dp(10),0,dp(10),0);b.setStateListAnimator(null);tint(b,fill,color);b.setOnClickListener(v->{if(android.animation.ValueAnimator.areAnimatorsEnabled()){b.setScaleX(.97f);b.animate().scaleX(1).setDuration(280).setInterpolator(new OvershootInterpolator()).start();}run.run();});return b;}
    private void addPair(LinearLayout row,View a,View b,int h){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(h),1);lp.rightMargin=dp(8);row.addView(a,lp);row.addView(b,new LinearLayout.LayoutParams(0,dp(h),1));}
    private View link(String title,String subtitle,Runnable click,boolean arrow){
        LinearLayout content=vertical();content.addView(label(title,17,ink));content.addView(label(subtitle,12,sub));
        LinearLayout line=row();line.setPadding(0,dp(8),0,dp(8));line.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        if(arrow){TextView chevron=label("›",28,sub);chevron.setPadding(dp(16),0,dp(4),0);chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);line.addView(chevron);}
        line.setOnClickListener(v->click.run());line.setMinimumHeight(dp(56));return line;
    }
    @Override protected void onResume(){super.onResume();resumed=true;handler.post(poll);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(poll);super.onPause();}
    private final class Preview extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Preview(Context c){super(c);}
        void rect(Canvas c,float l,float t,float r,float b,float rad,int color){p.setColor(color);c.drawRoundRect(new RectF(l,t,r,b),rad,rad,p);}
        @Override protected void onDraw(Canvas canvas){
            float h=getHeight()-dp(12),w=h*.46f,x=(getWidth()-w)/2,y=dp(4);rect(canvas,x-dp(5),y-dp(5),x+w+dp(5),y+h+dp(5),dp(20),ink);
            rect(canvas,x,y,x+w,y+h,dp(15),c("#9FB1A3"));float s=Geometry.percentScale(percent),cw=w*s,ch=h*s,cx=left?x:x+w-cw,cy=y+h-ch;
            rect(canvas,cx,cy,cx+cw,cy+ch,dp(15)*s,c("#F9FFFA"));float inset=dp(5)*s;
            rect(canvas,cx+inset,cy+dp(15)*s,cx+cw-inset,cy+dp(39)*s,dp(6)*s,c("#ADD7BA"));
            for(int j=0;j<4;j++)rect(canvas,cx+inset,cy+(50+j*13)*s*getResources().getDisplayMetrics().density,cx+cw-inset,cy+(55+j*13)*s*getResources().getDisplayMetrics().density,dp(2),c("#D6E6DA"));
            p.setColor(sub);p.setTextSize(dp(14));p.setTextAlign(Paint.Align.CENTER);canvas.drawText(percent+"%",getWidth()*.83f,getHeight()*.48f,p);
            p.setTextSize(dp(11));canvas.drawText(left?"左手":"右手",getWidth()*.83f,getHeight()*.60f,p);
        }
    }
}
