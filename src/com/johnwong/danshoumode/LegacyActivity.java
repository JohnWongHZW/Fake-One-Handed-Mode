package com.johnwong.danshoumode;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Native UI following the M3 Expressive visual language; no network or root client. */
public final class LegacyActivity extends Activity {
    private TextView state, pill;
    private Button toggle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed, pending;
    private String diagnostics="模块尚未连接，请在 LSP 启用「系统框架（system）」作用域并重启。";
    private int bg, surface, ink, secondary, accent, onAccent, container;
    private final Runnable poll = new Runnable() {
        public void run() { if (resumed) { request("status"); handler.postDelayed(this, 1000); } }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        bg = color(dark ? "#14121B" : "#FAF7FF");
        surface = color(dark ? "#24212D" : "#F0EAF7");
        ink = color(dark ? "#F1EBFA" : "#231D30");
        secondary = color(dark ? "#CAC2D6" : "#655D73");
        accent = color(dark ? "#CDBBFF" : "#6550A4");
        onAccent = color(dark ? "#302054" : "#FFFFFF");
        container = color(dark ? "#3F315B" : "#EADDFF");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(bg); scroll.setClipToPadding(false);
        LinearLayout column = vertical(); column.setPadding(dp(20),dp(12),dp(20),dp(28)); scroll.addView(column);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        LinearLayout header=row();
        ImageView avatar=new ImageView(this);
        avatar.setImageResource(getResources().getIdentifier("portrait","drawable",getPackageName()));
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(shape(container,20)); avatar.setClipToOutline(true); avatar.setContentDescription("卢本伟模式图标");
        header.addView(avatar,new LinearLayout.LayoutParams(dp(60),dp(60)));
        LinearLayout names=vertical(); names.setPadding(dp(14),0,0,0);
        TextView title=label("卢本伟模式",28,ink); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        names.addView(title); names.addView(label("流浪本子商人",13,secondary));
        header.addView(names,new LinearLayout.LayoutParams(0,-2,1)); column.addView(header); space(column,22);
        LinearLayout hero=card(container,32,20);
        LinearLayout heroTop=row(); TextView heroTitle=label("我卢本伟没有开挂",23,ink);
        heroTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        heroTop.addView(heroTitle,new LinearLayout.LayoutParams(0,-2,1));
        pill=label("检测中",12,accent); pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(11),dp(7),dp(11),dp(7)); pill.setBackground(shape(bg,30)); heroTop.addView(pill);
        hero.addView(heroTop); space(hero,12);
        ModePreview preview=new ModePreview(this);
        preview.setContentDescription("效果示意：完整画面等比缩至横屏最左侧，右侧为黑色");
        hero.addView(preview,new LinearLayout.LayoutParams(-1,dp(125))); space(hero,10);
        hero.addView(label("完整画面 · 等比缩小　开启会禁用媒体声音",13,secondary)); column.addView(hero); space(column,16);
        toggle=button("开启卢本伟模式",accent,onAccent,30,() -> request("toggle"));
        toggle.setEnabled(false); toggle.setAlpha(.55f); column.addView(toggle,new LinearLayout.LayoutParams(-1,dp(64)));
        state=label("正在连接模块…",13,secondary); state.setPadding(dp(8),dp(10),dp(8),dp(10));
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); column.addView(state);
        LinearLayout actions=row();
        Button restore=button("紧急恢复",surface,ink,22,() -> request("stop"));
        Button help=button("使用教程",surface,ink,22,this::showHelp);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(52),1); left.rightMargin=dp(8);
        actions.addView(restore,left); actions.addView(help,new LinearLayout.LayoutParams(0,dp(52),1));
        column.addView(actions); space(column,8);
        column.addView(button("复制诊断信息",surface,ink,22,() -> {
            ClipboardManager clipboard=getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(ClipData.newPlainText("卢本伟模式诊断",diagnostics));
            Toast.makeText(this,"诊断信息已复制，可直接粘贴反馈",Toast.LENGTH_SHORT).show();
        }),new LinearLayout.LayoutParams(-1,dp(48)));
        space(column,18);
        TextView note=label("卢本伟模式 · 作者的妙妙小功能",12,secondary);
        note.setGravity(Gravity.CENTER); column.addView(note); setContentView(scroll);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(android.os.Process.myUid()/100000!=0) state.setText("仅支持主用户，请勿在分身或工作资料中运行");
    }
    private int color(String value){return Color.parseColor(value);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable shape(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout card(int fill,int radius,int padding){LinearLayout v=vertical();v.setBackground(shape(fill,radius));v.setPadding(dp(padding),dp(padding),dp(padding),dp(padding));return v;}
    private void space(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private Button button(String value,int fill,int textColor,int radius,Runnable action){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(textColor);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setPadding(dp(12),0,dp(12),0);b.setMinHeight(dp(48));b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x225F42A0),shape(fill,radius),null));
        b.setOnTouchListener((v,e)->{
            if(ValueAnimator.areAnimatorsEnabled()){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN)v.animate().scaleX(.97f).scaleY(.97f).setDuration(100).start();
                else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1).scaleY(1).setDuration(320).setInterpolator(new OvershootInterpolator(1.1f)).start();
            }return false;
        });
        b.setOnClickListener(v->action.run());return b;
    }
    private void showHelp(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(bg);
        TextView body=label("开启卢本伟模式\n点「开启卢本伟模式」后横拿手机。完整界面会等比缩到横屏左侧，其余区域留黑；媒体音量会暂时归零。\n\n"+
                "关闭与恢复\n再次点切换按钮，或点「紧急恢复」，即可恢复显示和原媒体音量。2 秒内连按 3 次音量减也可退出。\n\n"+
                "熄屏与时限\n开启后直接生效，熄屏不会关闭，亮屏后继续使用。单次最多运行 2 小时，重启后默认关闭。\n\n"+
                "音频设备变化\n更换耳机、蓝牙或其他媒体输出时会退出。切回原输出后恢复原音量，不会覆盖新设备的音量；闹钟、铃声和通话音量不受此功能调整。\n\n"+
                "遇到异常\n优先使用「紧急恢复」或连按音量减。若手机完全无响应，请强制重启；可复制诊断信息反馈问题。停用模块或卸载前，请先退出并确认音量恢复。",15,ink);
        body.setPadding(dp(24),dp(12),dp(24),dp(24));scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("使用教程").setView(scroll).setPositiveButton("知道了",null).show();
    }
    private void request(String op){
        if(pending||android.os.Process.myUid()/100000!=0)return;
        pending=true;Intent i=new Intent(Protocol.ACTION).setPackage("android");i.putExtra("op",op);
        try{
            sendOrderedBroadcast(i,null,new BroadcastReceiver(){
                @Override public void onReceive(Context c,Intent intent){
                    pending=false;if(isFinishing()||isDestroyed())return;
                    Bundle b=getResultExtras(false);
                    if(getResultCode()!=1||b==null){
                        state.setText("未收到系统进程回复：请启用「系统框架（system）」作用域并重启。若仍无回复，请查看 LSP 的伪单手模式日志。");pill.setText("未连接");
                        toggle.setEnabled(false);toggle.setAlpha(.55f);return;
                    }
                    diagnostics=b.getString("diagnostics","")+"\n"+b.getString("runtime","")+"\nStatus: "+b.getString("message","");
                    boolean active=b.getBoolean("active");
                    String description=b.getString("message","");
                    if(!description.contentEquals(state.getText()))state.setText(description);
                    boolean enabled=b.getBoolean("ready")||active;toggle.setEnabled(enabled);toggle.setAlpha(enabled?1:.55f);
                    toggle.setText(active?"关闭并恢复原音量":"开启卢本伟模式");
                    pill.setText(active?"已开启":(enabled?"已就绪":(b.getBoolean("pendingAudio")?"待恢复":"需适配")));
                }
            },handler,0,null,null);
        }catch(Throwable t){pending=false;state.setText("无法连接模块："+t.getClass().getSimpleName());}
    }
    @Override protected void onResume(){super.onResume();resumed=true;handler.post(poll);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(poll);super.onPause();}
    private final class ModePreview extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ModePreview(Context c){super(c);}
        private void rect(Canvas c,float x,float y,float w,float h,float radius,int color){paint.setColor(color);c.drawRoundRect(new RectF(x,y,x+w,y+h),radius,radius,paint);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight(),x=dp(2),y=dp(4),pw=w-dp(4),ph=h-dp(8);
            rect(c,x,y,pw,ph,dp(22),color("#292332"));rect(c,x+dp(5),y+dp(5),pw-dp(10),ph-dp(10),dp(18),Color.BLACK);
            float ch=ph-dp(14),cw=Math.min(pw/3-dp(8),ch*.48f),cx=x+dp(7),cy=y+dp(7);
            rect(c,cx,cy,cw,ch,dp(12),color("#F1EAFF"));rect(c,cx+dp(6),cy+dp(10),cw-dp(12),dp(18),dp(7),color("#B69DF4"));
            for(int j=0;j<3;j++)rect(c,cx+dp(6),cy+dp(36+j*15),cw-dp(12),dp(8),dp(3),color("#D0C0EF"));
            paint.setColor(color("#AFA4BD"));paint.setTextSize(dp(11));paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("其余区域保持黑色",x+pw*.65f,y+ph*.53f,paint);
        }
    }
}
