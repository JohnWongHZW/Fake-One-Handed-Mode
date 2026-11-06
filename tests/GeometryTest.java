import com.johnwong.danshoumode.Geometry;

public final class GeometryTest {
    private static int checks;
    private static void check(boolean ok, String msg) {
        checks++; if (!ok) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        int[][] devices = {{1440,3200},{1080,2400},{1200,2608},{1440,3120},{720,1280}};
        int[][] contents = {{1440,3200},{1080,2400},{3200,1440},{800,800},{1080,1920}};
        for (int[] panel : devices) for (int[] logical : contents) {
            int[] r = Geometry.fitLeftThird(panel[0],panel[1],logical[0],logical[1]);
            int w=r[2]-r[0], h=r[3]-r[1];
            check(r[0]==0,"left aligned");
            check(w>0 && h>0,"nonempty");
            check(r[2]<=panel[1]/3,"within left third");
            check(r[1]>=0 && r[3]<=panel[0],"vertical bounds");
            check(Math.abs((double)w/logical[0]-(double)h/logical[1]) <=
                    1.0/logical[0]+1.0/logical[1],"aspect ratio up to pixel rounding");
            // Check round-trip coordinate mapping for corners and interior points.
            for (double u : new double[]{0,.2,.5,.9,1}) for (double v : new double[]{0,.3,.6,1}) {
                double px = r[0] + u*w, py = r[1]+v*h;
                check(Math.abs((px-r[0])/w*logical[0]-u*logical[0])<1e-7,"touch x roundtrip");
                check(Math.abs((py-r[1])/h*logical[1]-v*logical[1])<1e-7,"touch y roundtrip");
            }
        }
        for (int[] bad : new int[][]{{0,2400,1080,2400},{2400,1080,1080,2400},{1080,2400,0,2400}}) {
            boolean rejected=false;
            try {Geometry.fitLeftThird(bad[0],bad[1],bad[2],bad[3]);}
            catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"invalid screen rejected");
        }
        for(int[] panel:devices)for(int percent:new int[]{40,53,60,70,80,85})for(boolean left:new boolean[]{false,true}){
            float scale=Geometry.percentScale(percent);int[] r=Geometry.handFrame(panel[0],panel[1],scale,left);
            float[] inv=Geometry.inverseHandMatrix(scale,r[0],r[1]);
            float px=r[0]+panel[0]*.31f*scale,py=r[1]+panel[1]*.79f*scale;
            check(Math.abs(inv[0]*px+inv[2]-panel[0]*.31f)<.001f &&
                    Math.abs(inv[4]*py+inv[5]-panel[1]*.79f)<.001f,"vendor input maps physical point to task coordinates");
            for(int[] image:new int[][]{{64,29},{29,64},{64,64}}){
                int w=r[2]-r[0],h=r[3]-r[1];
                float[] fill=Geometry.wallpaperBounds(image[0],image[1],w,h);
                check(fill[0]<=.001f && fill[1]<=.001f && fill[2]>=w-.001f && fill[3]>=h-.001f,"blurred wallpaper covers all four local panel edges");
                check(Math.abs((fill[2]-fill[0])/image[0]-(fill[3]-fill[1])/image[1])<.001f,"wallpaper aspect ratio retained");
            }
            check(r[3]==panel[1]&&(left?r[0]==0:r[2]==panel[0]),"bottom corner anchor");
            check(Math.abs((r[2]-r[0])/(float)panel[0]-(r[3]-r[1])/(float)panel[1])<.002,"one hand uniform scale");
            check(Geometry.insideRounded((r[0]+r[2])/2f,(r[1]+r[3])/2f,r,40),"center interactive");
            check(!Geometry.insideRounded(r[0],r[1],r,40),"rounded-away corner counts as outside");
            check(!Geometry.insideRounded(r[2]+1,r[1],r,40),"blank exterior excluded");
            check(Geometry.insideRounded(r[0]+40,r[1],r,40),"round tangent inside");
        }
        for(int bad:new int[]{-1,0,39,86,100,Integer.MAX_VALUE}){
            boolean rejected=false;try{Geometry.percentScale(bad);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"invalid percentage rejected");
        }
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1f,0f,1f}){
            boolean rejected=false;try{Geometry.handFrame(1440,3200,bad,false);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected,"invalid one hand scale rejected");
        }
        System.out.println("Geometry: " + checks + " assertions passed (host simulation, not Android input hardware)");
    }
}
