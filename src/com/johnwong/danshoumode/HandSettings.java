package com.johnwong.danshoumode;

import android.content.Intent;
import android.util.AtomicFile;
import java.io.*;
import java.util.Properties;

final class HandSettings {
    boolean left=false;
    int percent=60;
    private final AtomicFile file=new AtomicFile(new File("/data/system/johnwong-danshou-config.properties"));
    float scale(){return Geometry.percentScale(percent);}
    HandSettings copy(){HandSettings p=new HandSettings();p.left=left;p.percent=percent;return p;}
    void load() throws Exception {
        if(!file.getBaseFile().exists())return;
        Properties p=new Properties();try(FileInputStream in=file.openRead()){p.load(in);}
        int value=Integer.parseInt(p.getProperty("percent","60"));
        Geometry.percentScale(value);percent=value;left="left".equals(p.getProperty("side"));
    }
    HandSettings changed(Intent i) {
        HandSettings next=copy();int value=i.getIntExtra("percent",percent);
        String side=i.getStringExtra("side");if(side==null)side=left?"left":"right";
        if(!side.equals("left")&&!side.equals("right"))throw new IllegalArgumentException("未知左右手选项");
        Geometry.percentScale(value);next.percent=value;next.left=side.equals("left");return next;
    }
    void save(HandSettings next) throws Exception {
        next.scale();Properties p=new Properties();p.setProperty("percent",Integer.toString(next.percent));p.setProperty("side",next.left?"left":"right");
        FileOutputStream out=null;
        try {out=file.startWrite();p.store(out,"One-handed mode preferences");file.finishWrite(out);}
        catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
        percent=next.percent;left=next.left;
    }
}
