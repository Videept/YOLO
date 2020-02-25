package ie.tcd.netlab.objecttracker.detectors;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.graphics.RectF;
import android.media.Image;
import android.os.Build;
import android.util.Size;
import android.widget.Toast;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import org.opencv.core.Mat;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;

import java.nio.ByteBuffer;
import java.net.Socket;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.io.IOException;

import ie.tcd.netlab.objecttracker.helpers.Recognition;
import ie.tcd.netlab.objecttracker.helpers.Transform;
import ie.tcd.netlab.objecttracker.testing.Logger;

@TargetApi(21)

public class DetectorYoloHTTP extends Detector {


    /**Note to Abhinav  - All these variables are used for calling elsewhere in the app**/

    private final int jpegQuality;
    private InetAddress IP;
    ByteBuffer IPbuf;
    //Abhinav
    Bitmap prev = null;
    private final String server;
    private final int port;
    private final boolean useUDP;
    private int udpsockfd=-1;
    private Socket tcpsock;

    BufferedOutputStream out;

    BufferedReader in;
    private final static int LISTSIZE=1000; // if change this then also change value in udp_socket_jni.c
    ByteBuffer recvbuf, image_bytes, req_buf;
    private final static int MSS=1472;          // max UDP payload (assuming 1500B packets)
    private static final boolean DEBUGGING = false;  // generate extra debug output ?

    static {
        System.loadLibrary("udpsocket");
    }
    private native int socket(ByteBuffer addr, int port);
    private native void closesocket(int fd);
    private native String sendto(int fd, ByteBuffer sendbuf, int offset, int len, int MSS);
    private native String sendmmsg(int fd, ByteBuffer req, int req_len, ByteBuffer img, int img_len, int MSS);
    private native int recv(int fd, ByteBuffer recvbuf, int len, int MSS);
    /*private native void keepalive();*/

    public DetectorYoloHTTP(@NonNull Context context, String server, int jpegQuality, boolean useUDP) {
        String parts[] = server.split(":");
        this.server=parts[0]; this.port=Integer.valueOf(parts[1]); //server details
        this.IP=null; // this will force DNS resolution of server name in background thread below
        // (since it may take a while and anyway DNS on the UI thread is banned by android).
        this.jpegQuality = jpegQuality;
        this.useUDP = useUDP;
        this.tcpsock = null;
        // can't open sockets here as may not yet have internet permission
        // only open them once, so that tcp syn-synack handshake is not repeated for every image
        if (!hasPermission(context)) { // need internet access to use YoloHTTP
            requestPermission((Activity) context);
        }



        // allocate byte buffers used to pass data to jni C
        recvbuf = ByteBuffer.allocateDirect(MSS*LISTSIZE);
        IPbuf = ByteBuffer.allocateDirect(4); // size of an IPv4 address
        image_bytes=ByteBuffer.allocateDirect(MSS*LISTSIZE);
        req_buf=ByteBuffer.allocateDirect(MSS);

    }



    protected void finalize() {
        if (udpsockfd >0) {
            closesocket(udpsockfd);
        }
        if (this.tcpsock != null) {
            try {
                this.tcpsock.close();
            } catch(Exception e) {
                Logger.addln("\nWARN Problem closing TCP socket ("+e.getMessage()+")");
            }
        }
    }

    public Detections recognizeImage(Image image, int rotation) {
        // take Image as input, convert to byte array and then call recognise()

        if (image.getFormat() != ImageFormat.YUV_420_888) {
            // unsupported image format
            Logger.addln("\nWARN YoloHTTP.recognizeImage() unsupported image format");
            return new Detections();
        }
        byte[] d=Transform.yuvBytes(image);
        return recognize(Transform.yuvBytes(image), image.getWidth(),image.getHeight(), rotation);
    }
    public static byte[] convertBitmapToByteArray(Bitmap bitmap){
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return baos.toByteArray();
        }finally {
            if(baos != null){
                try {
                    baos.close();
                } catch (IOException e) {
                    //Log.e(BitmapUtils.class.getSimpleName(), "ByteArrayOutputStream was not closed");
                }
            }
        }
    }
    public static byte[] convert(Bitmap bitmap){
        ByteBuffer byteBuffer = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(byteBuffer);
        byteBuffer.rewind();
        return byteBuffer.array();
    }

    @Override
    public Detections recognize(byte[] yuv, int image_w, int image_h, int rotation) {
        // takes yuv byte array as input
        Detections detects = new Detections();


        //V - Differencing on bitmap
        Bitmap bmp = Bitmap.createBitmap(image_w, image_h, Bitmap.Config.ARGB_8888);
        ByteBuffer buffer = ByteBuffer.wrap(yuv);
        //bmp.copyPixelsFromBuffer(buffer);

        Bitmap diff=bmp;
        int a_height = bmp.getHeight();
        int a_width = bmp.getWidth();
        if(prev!=null){
            for(int i=0;i<a_width;i++){
                int x;
                for(int j = 0; j<a_height; j++){
                     x = bmp.getPixel(i, j) - prev.getPixel(i, j);
                diff.setPixel(i,j,x);
                }
            }
        }
        prev = bmp;

        //Abhinav - changing the bitmap to byte now
        /**width = bitmap.getWidth();
         height = bitmap.getHeight();**/

        //int size = bitmapImage.getRowBytes() * a_height;
        //ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        //bitmapImage.copyPixelsToBuffer(byteBuffer);
        //byte[] byteArray = byteBuffer.array();

        //yuv = byteArray;

        //for loop
        //bitmapImage.getPixel()
        /**ABHINAV**/
        /**Can this line work as the input storing the images of a video???**/
        /**List<Recognition> recognitions = new ArrayList<>();**/

        Logger.tick("d");
        Logger.tick("yuvtoJPG");
        /**Abhinav**/
        /**TRANSFORMING THE IMAGE FROM YUV TO JPEG FORM USING Transform.YUVtoJPEG**/
        int isYUV;
        image_bytes.clear();
        if (jpegQuality>0) {
            // we do rotation server-side, android client too slow (takes around 10ms in both java
            // and c on Huawei P9, while jpeg compression takes around 8ms).
            try {
                image_bytes.put(Transform.YUVtoJPEG(yuv, image_w, image_h, jpegQuality));
                isYUV = 0;
            } catch (Exception e) {
                // most likely encoded image is too big for image_bytes buffer
                Logger.addln("WARN: Problem encoding jpg: "+e.getMessage());
                return detects; // bail
                /**Ask KARAN what this will do,
                 * after sending to detects again how is it going to get a new image??? **/
            }
        } else {
            // send image uncompressed
            image_bytes.put(yuv);
            isYUV=1;
        }
        detects.addTiming("yuvtoJPG",Logger.tockLong("yuvtoJPG"));

        /**ask if this happening in the next lines of code
         * rotate image to align with camera view,
         * and scale to yolo input size**/

        int dst_w=image_w, dst_h=image_h;
        if ((rotation%180 == 90) || (rotation%180 == -90)) {
            dst_w = image_h; dst_h = image_w;
        }
        Matrix frameToViewTransform = Transform.getTransformationMatrix(
                image_w, image_h,
                dst_w, dst_h,
                rotation, false);
        // used to map received response rectangles back to handset view
        Matrix viewToFrameTransform = new Matrix();
        frameToViewTransform.invert(viewToFrameTransform);

        if (IP==null) {
            // resolve server name to IP address
            try {
                InetAddress names[] = InetAddress.getAllByName(server);
                StringBuilder n = new StringBuilder();
                for (InetAddress name : names) {
                    n.append(name);
                    if (name instanceof Inet4Address) {IP = name; break;}
                }
                Logger.addln("\nResolved server to: "+IP);
                if (IP == null) {
                    Logger.addln("\nWARN Problem resolving server: "+n);
                    return detects;
                }

            } catch (IOException e) {
                Logger.addln("\nWARNProblem resolving server "+server+" :"+e.getMessage());
                return detects;
            }
        }

        String req = "POST /api/edge_app2?r=" + rotation
                + "&isYUV=" + isYUV + "&w="+ image_w + "&h="+image_h
                + " HTTP/1.1\r\nContent-Length: " + image_bytes.position() + "\r\n\r\n";
        StringBuilder response = new StringBuilder();
        if (useUDP) {
            try {
                Logger.tick("url2");
                // open connection (if not already open) and send request+image
                if (udpsockfd <0) {
                    // put the server IP address into a byte buffer to make it easy to pass to jni C
                    IPbuf.position(0);
                    IPbuf.put(IP.getAddress());
                    udpsockfd=socket(IPbuf,port);
                    Debug.println("sock_fd="+udpsockfd);
                }
                Debug.println("data len=("+req.length()+","+image_bytes.position()+")");
                Logger.tick("url2a");
                // copy request to byte buffer so easy to pass to jni C
                req_buf.clear();
                req_buf.put(req.getBytes(),0,req.length());
                String str = sendmmsg(udpsockfd, req_buf, req.length(), image_bytes, image_bytes.position(), MSS);
                Debug.println("s: "+str);
                //Logger.add("s: "+str);
                detects.addTiming("url2a",Logger.tockLong("url2a"));
                detects.addTiming("url2",Logger.tockLong("url2"));
                int count=1+(req.length()+image_bytes.position())/(MSS-2);
                detects.addTiming("pkt count", count*1000);

                // read the response ...
                Logger.tick("url3");
                // need to receive on same socket as used for sending or firewall blocks reception
                int resplen = recv(udpsockfd, recvbuf, MSS*LISTSIZE, MSS);
                if (resplen<0) {
                    Logger.addln("\nWARN UDP recv error: errno="+resplen);
                } else if (resplen==0) {
                    Logger.addln("\nWARN UDP timeout");
                } else {
                    response.append(new String(recvbuf.array(), recvbuf.arrayOffset(), resplen));
                }
                if (response.length()<=10) {
                    Debug.println(" received " + response.length());
                }
                detects.addTiming("url3",Logger.tockLong("url3"));
                Logger.addln(detects.client_timings.toString());
                //String pieces[] = response.split("\n");
                //response = pieces[pieces.length-1];  // ignore all the headers (shouldn't be any !)
            } catch(Exception e) {
                Logger.addln("\nWARN Problem with UDP on "+IP+":"+port+" ("+e.getMessage()+")");
            }
        } else { // use TCP
            try {
                // open connection and send request+image
                Logger.tick("url2");
                if (tcpsock == null) {
                    tcpsock = new Socket(IP, port);
                    out = new BufferedOutputStream(tcpsock.getOutputStream());
                    in = new BufferedReader(new InputStreamReader(tcpsock.getInputStream()));
                }
                try {
                    out.write(req.getBytes());
                    out.write(image_bytes.array(),image_bytes.arrayOffset(),image_bytes.position());
                    out.flush();
                } catch(IOException ee) {
                    // legacy server closes TCP connection after each response, in which case
                    // we reopen it here.
                    Logger.addln("Retrying TCP: "+ee.getMessage());
                    tcpsock.close();
                    tcpsock = new Socket(IP, port);
                    out = new BufferedOutputStream(tcpsock.getOutputStream());
                    in = new BufferedReader(new InputStreamReader(tcpsock.getInputStream()));
                    out.write(req.getBytes());
                    out.write(image_bytes.array());
                    out.flush();
                }
                detects.addTiming("url2",Logger.tockLong("url2"));

                Logger.tick("url3");
                // read the response ...
                // read the headers, we ignore them all !
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.length() == 0) break; // end of headers, stop
                }
                // now read to end of response
                response.append(in.readLine());
                detects.addTiming("url3",Logger.tockLong("url3"));
            } catch(Exception e) {
                Logger.addln("\nWARN Problem connecting TCP to "+IP+":"+port+" ("+e.getMessage()+")");
                try {
                    tcpsock.close();
                } catch(Exception ee) {};
                tcpsock = null; // reset connection
            }
        }
        if (response.length()==0 || response.toString().equals("null")) {
            Logger.add(" empty response");
            Logger.add(": "+Logger.tock("d"));
            return detects; // server has dropped connection
        }
        // now parse the response as json ...
        try {
            // testing
            //response = "{"server_timings":{"size":91.2,"r":0.4,"jpg":8.4,"rot":34.1,"yolo":48.3,"tot":0},"results":[{"title":"diningtable","confidence":0.737176,"x":343,"y":415,"w":135,"h":296},{"title":"chair","confidence":0.641756,"x":338,"y":265,"w":75,"h":57},{"title":"chair","confidence":0.565877,"x":442,"y":420,"w":84,"h":421}]}
            //              [{"title":"diningtable","confidence":0.737176,"x":343,"y":415,"w":135,"h":296},{"title":"chair","confidence":0.641756,"x":338,"y":265,"w":75,"h":57},{"title":"chair","confidence":0.565877,"x":442,"y":420,"w":84,"h":421}]
            //              cam: 39 {"yuvtoJPG":8,"url2":15,"url3":128,"d":152}"
            JSONObject json_resp = new JSONObject(response.toString());
            JSONArray json = json_resp.getJSONArray("results");
            int i; JSONObject obj;
            for (i = 0; i < json.length(); i++) {
                obj = json.getJSONObject(i);
                String title = obj.getString("title");
                Float confidence = (float) obj.getDouble("confidence");
                Float x = (float) obj.getInt("x");
                Float y = (float) obj.getInt("y");
                Float w = (float) obj.getInt("w");
                Float h = (float) obj.getInt("h");
                RectF location = new RectF(
                        Math.max(0, x - w / 2),  // left
                        Math.max(0, y - h / 2),  // top
                        Math.min(dst_w - 1, x + w / 2),  //right
                        Math.min(dst_h - 1, y + h / 2));  // bottom
                viewToFrameTransform.mapRect(location); // map boxes back to original image coords
                Recognition result = new Recognition(title, confidence, location, new Size(image_w, image_h));
                detects.results.add(result);
            }
            detects.server_timings = json_resp.getJSONObject("server_timings");
        } catch(Exception e) {
            Logger.addln("\nWARN Problem reading JSON:  "+response+" ("+e.getMessage()+")");
        }
        detects.addTiming("d",Logger.tockLong("d"));
        return detects;
    }

    /***************************************************************************************/
    private boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission(final Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,Manifest.permission.INTERNET)) {
                // send message to user ...
                activity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity,
                                        "Internet permission is required to use YoloHTTP",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }
            ActivityCompat.requestPermissions(activity,new String[]{Manifest.permission.INTERNET},
                    2);
            // will enter onRequestPermissionsResult() callback in class cameraFragment following
            // user response to permissions request (bit messy that its hidden inside that class,
            // should probabyl tidy it up).

        }
    }

    /***************************************************************************************/
    // debugging
    private static class Debug {
        static void println(String s) {
            if (DEBUGGING) System.out.println("YoloHTTP: "+s);
        }
    }
}
