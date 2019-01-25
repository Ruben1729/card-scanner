package scanner.cards.app.cardscanner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String API_URL = "https://rubensanchez.co/api/";

    private Paint paint;

    private CameraSource cameraSource;
    private SurfaceView cameraView;
    private SurfaceView drawView;
    private TextView textView;
    private String fetchURL;//EXAMPLE: http://gatherer.wizards.com/Pages/Search/Default.aspx?name=+[]+[Nicol]+[Bolas,]+[d-Pharaoh&]

    private Sets mtgSets;

    private int RequestCameraPermissionID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mtgSets = new Sets().update();


        paint = new Paint();
        paint.setStrokeWidth(10);
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);

        cameraView = findViewById(R.id.surfaceView);
        textView = findViewById(R.id.picTxt);
        drawView = findViewById(R.id.drawView);

        drawView.setZOrderOnTop(true);
        SurfaceHolder sfhTrackHolder = drawView.getHolder();
        sfhTrackHolder.setFormat(PixelFormat.TRANSPARENT);

        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        if(!textRecognizer.isOperational())
            Log.w("MainActivity",  "Detector dependencies are not yet available");
        else{

            cameraSource = new CameraSource.Builder(getApplicationContext(), textRecognizer)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setRequestedPreviewSize(1280, 1080)
                    .setRequestedFps(30.0f)
                    .setAutoFocusEnabled(false)
                    .build();

            cameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {

                    try{
                        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, RequestCameraPermissionID);
                            return;
                        }
                        cameraSource.start(cameraView.getHolder());

                    }catch(Exception e){
                        e.printStackTrace();
                    }

                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    cameraSource.stop();
                }
            });



            textRecognizer.setProcessor(new Detector.Processor<TextBlock>() {
                @Override
                public void release() {

                }

                @Override
                public void receiveDetections(Detector.Detections<TextBlock> detections) {
                    final SparseArray<TextBlock> items = detections.getDetectedItems();
                    if(items.size() > 0){
                        textView.post(new Runnable() {
                            @Override
                            public void run() {
                                StringBuilder sb = new StringBuilder();
                                for(int i = 0; i < items.size(); i++)
                                    sb.append(items.valueAt(i).getValue() + ";");
                                textView.setText(sb.toString());

                                Canvas canvas = drawView.getHolder().lockCanvas();
                                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                                float scaleW = (float)canvas.getWidth() / (float)cameraSource.getPreviewSize().getHeight();
                                float scaleH = (float)canvas.getHeight() / (float)cameraSource.getPreviewSize().getWidth();

                                for(int i = 0; i < items.size(); i++){

                                    TextBlock textBlock = items.valueAt(i);

                                    Rect rect = scale(textBlock.getBoundingBox(), scaleW, scaleH);
                                    canvas.drawRect(rect, paint);

                                }

                                drawView.getHolder().unlockCanvasAndPost(canvas);
                            }
                        });
                    }
                }
            });
        }

        Button btn = findViewById(R.id.addBtn);

        btn.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {

                String input = textView.getText().toString();

                int cn = -1;
                String setAcro = "";

                cn = getCollectorNumber(input.trim());
                setAcro = getSetAcro(input.trim());
                if(isToken(input))
                    setAcro = "T" + setAcro;

                String keywords = textView.getText().toString().split(";")[0].toLowerCase();

                final String msg = "=========== SCAN RESULTS ===========\n"
                                +  "Collector's Number : \t\t" + Integer.toString(cn) + "\n"
                                +  "Set Code : \t\t\t\t\t\t" + setAcro + "\n"
                                +  "Keywords : \t\t\t\t" + keywords;

                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();

                fetchURL = "https://scryfall.com/search?q=set%3A" + setAcro + "+number%3A" + cn;//example: https://scryfall.com/search?q=set%3Aa25+number%3A7

                System.out.println("URL to get info from: " + fetchURL);

                try{

                    JSONObject json = new JSONObject();

                    JSONObject card = new JSONObject();
                    card.put("cn",  cn);
                    card.put("sa", setAcro);
                    card.put("keywords", keywords);

                    JSONArray cardList = new JSONArray();
                    cardList.put(0, card);

                    json.put("cards", cardList);

                    final String postBody = json.toString();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                HttpClient httpClient = new DefaultHttpClient();
                                HttpPost post = new HttpPost(API_URL + "new_cards.php");
                                post.addHeader("content-type", "application/json");
                                post.setEntity(new StringEntity(postBody));
                                HttpResponse response = httpClient.execute(post);
                                System.out.println(response.getStatusLine().getStatusCode());
                                InputStream is = response.getEntity().getContent();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "windows-1251"), 8);

                                StringBuilder sb = new StringBuilder();
                                String line = null;
                                while((line = reader.readLine()) != null)
                                {

                                    sb.append(line + "\n");

                                }

                                System.out.println(sb.toString());
                            }catch(Exception e){
                                e.printStackTrace();
                            }
                        }
                    }).start();


                    // message
                    // {"course":[{"id":3,"information":"test","name":"course1"}],"name":"student"}

                }catch(Exception e)
                {

                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Error while sending data to server.", Toast.LENGTH_SHORT).show();

                }

            }

        });

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int pointerId = event.getPointerId(0);
        int pointerIndex = event.findPointerIndex(pointerId);
        // Get the pointer's current position
        int[] coords = new int[2];
        cameraView.getLocationOnScreen(coords);
        float x = event.getX(pointerIndex) - coords[0];
        float y = event.getY(pointerIndex) - coords[1];

        Rect touchRect = new Rect(
                (int) (x - 150),
                (int) (y - 150),
                (int) (x + 150),
                (int) (y + 150) );
        final Rect targetFocusRect = new Rect(
                touchRect.left * 2000 / cameraView.getWidth() - 1000,
                touchRect.top * 2000 / cameraView.getHeight() - 1000,
                touchRect.right * 2000 / cameraView.getWidth() - 1000,
                touchRect.bottom * 2000 / cameraView.getHeight() - 1000);

        focusCamera(targetFocusRect);

        System.out.println(coords[0] + "  " + coords[1]);
        System.out.println(x + "  " + y);

        System.out.println(targetFocusRect);

        return super.onTouchEvent(event);
    }

    private void focusCamera(Rect focusArea){
        try {
            Field[] declaredFields = CameraSource.class.getDeclaredFields();

            for (Field field : declaredFields) {
                if (field.getType() == Camera.class) {
                    field.setAccessible(true);
                    Camera camera = (Camera) field.get(cameraSource);

                    Camera.Parameters params = camera.getParameters();
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);

                    List<Camera.Area> focusList = new ArrayList();
                    Camera.Area area = new Camera.Area(focusArea, 1000);
                    focusList.add(area);
                    params.setFocusAreas(focusList);

                    camera.setParameters(params);

                    camera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            if (success)
                                camera.cancelAutoFocus();
                        }
                    });
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public String getSetAcro(String s)
    {

        Matcher m = Pattern.compile("[\\p{Upper}\\p{Digit}]{3}").matcher(s);

        while(m.find())
            if(mtgSets.exists(m.group()))
                return m.group();

        return "";

    }

    public int getCollectorNumber(String s) {

        int result = -1;

        Matcher m = Pattern.compile("\\w{3}/\\w{3}").matcher(s);

        if(!m.find())
            return result;

        String str = m.group();

        str = str.replace('O', '0').replace('o', '0');

        String strFirst = str.split("/")[0];

        try{
            result = Integer.parseInt(strFirst);
        }catch(NumberFormatException e){
            e.printStackTrace();
        }

        return result;
    }

    public boolean isToken(String s){
        if(s.contains(" T\n") || s.contains(" T ") || s.contains(" T;"))
            return true;
        return false;
    }

    private Rect scale(Rect rect, float scaleW, float scaleH){
        return new Rect((int)(rect.left * scaleW),
                        (int)(rect.top * scaleH),
                        (int)(rect.right * scaleW),
                        (int)(rect.bottom * scaleH));
    }

    public String getWebsite(String webURL)
    {

        String website = "";

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(webURL);

        try{

            HttpResponse response;
            response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            InputStream is = entity.getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "windows-1251"), 8);

            StringBuilder sb = new StringBuilder();
            String line = null;
            while((line = reader.readLine()) != null)
            {

                sb.append(line + "\n");

            }
            website = sb.toString();
            is.close();

        }catch(Exception e) {

            e.printStackTrace();

        }

        return website;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        if(requestCode == RequestCameraPermissionID){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                    return;
                try {
                    cameraSource.start(cameraView.getHolder());
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

}
