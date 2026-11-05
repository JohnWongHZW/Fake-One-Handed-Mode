package com.johnwong.danshoumode;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public final class HandTileService extends TileService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean listening,pending,queuedToggle;
    private final Runnable poll=new Runnable(){public void run(){if(listening){query("status");handler.postDelayed(this,1000);}}};
    @Override public void onStartListening(){super.onStartListening();listening=true;handler.removeCallbacks(poll);handler.post(poll);}
    @Override public void onStopListening(){listening=false;handler.removeCallbacks(poll);super.onStopListening();}
    @Override public void onClick(){super.onClick();if(isLocked())unlockAndRun(()->query("hand_toggle"));else query("hand_toggle");}
    private void query(String op) {
        if(pending){if("hand_toggle".equals(op))queuedToggle=true;return;}pending=true;
        AppBridge.request(this,op,state->{pending=false;update(state);if(queuedToggle){queuedToggle=false;query("hand_toggle");}});
    }
    private void update(Bundle b) {
        Tile t=getQsTile();if(t==null)return;
        boolean available=b.getBoolean("connected")&&b.getBoolean("handReady");
        boolean active=b.getBoolean("handActive");
        t.setLabel("伪单手模式");
        t.setState(available?(active?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE):Tile.STATE_UNAVAILABLE);
        t.setSubtitle(available?(("left".equals(b.getString("side"))?"左手":"右手")+" · "+b.getInt("percent",60)+"%"):"请启用模块并重启");
        t.setContentDescription("伪单手模式，"+(active?"已开启":"未开启"));t.updateTile();
    }
}
