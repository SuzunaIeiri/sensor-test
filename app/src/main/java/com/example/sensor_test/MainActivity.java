package com.example.sensor_test;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;


import static java.lang.Math.abs;
import java.util.HashMap;
import java.util.Map;
import java.lang.Double;


public class MainActivity extends AppCompatActivity implements SensorEventListener,View.OnClickListener {
    private SensorManager sensorManager;//センサーの値を取得するためのSensorManagerを管理
    private TextView textView;//センサーの値を表示するためのTextViewを持つ
    private String state;

    //センサ値クラス
    //getter setter なし
    //センサーの値を保持するためのSensorDataClassという内部クラス
    //センサーのx軸、y軸、z軸の値を保持するためのArrayList<Float>
    class SensorDataClass{
        ArrayList<Float> x;
        ArrayList<Float> y;
        ArrayList<Float> z;
        //センサーの各軸の値を取得した時刻を保持するためのArrayList<Long>
        ArrayList<Long> time;

        SensorDataClass() {
            //センサーの値を初期化するために呼び出され,各リストを空にして再初期化
            init();
        }

        public void init(){
            this.x = null;
            this.y = null;
            this.z = null;
            this.time = null;
            this.x = new ArrayList<>();
            this.y = new ArrayList<>();
            this.z = new ArrayList<>();
            this.time = new ArrayList<>();
        }
    }

    //SensorDataClass内部クラスのインスタンスを格納するための変数
    SensorDataClass accel_data = new SensorDataClass();
    SensorDataClass gyro_data = new SensorDataClass();
    SensorDataClass magnet_data = new SensorDataClass();
    SensorDataClass SMAac_data = new SensorDataClass();

    ArrayList<Long> passed_time;//センサーデータの取得間隔を記録するためのリスト

    long start_time;//センサーデータの取得を開始した時刻を記録するための変数
    long now_time;
    int hosu_stand_step_select;//歩数選択:0 立ち上がり選択:1 ステッピング選択:2
    File accelFile;//加速度データのファイル
    //    File gyrofile;
//    File magnetfile;
//    File SMAacFile;
    File axisPeakFile;//3軸加速度ピークデータのファイルを表す
    String TAG = "Main";//デバッグログに表示する際のタグ名を示すための変数

    // 定数
    private static final int REQUEST_ENABLEBLUETOOTH = 1; // Bluetooth機能の有効化要求時の識別コード
    private static final int REQUEST_CONNECTDEVICE   = 2; // デバイス接続要求時の識別コード
    private static final int READBUFFERSIZE          = 1024;    // 受信バッファーのサイズ
    private int INTERVAL_TIME           = 20; //測定間隔(mSec)
    private double PEAK_NUM           = 17; //ピーク検出用閾値(水平に手に持った時) 自然に持って振ったときは14.0前後必要
    private double LOW_PEAK_NUM           = 5.8; //下ピーク検出用閾値
    private double PERIOD           = 10;//一つ目のピークから次のピークまでの検出しない時間を設定する
    private double LOW_PERIOD           = 40;//一つ目の下ピークから次の下ピークまでの検出しない時間を設定する
    private double STAND_TIME        = 30000;//立ち上がりの計測時間を設定する
    private double STEP_TIME         = 20000;//ステッピングの計測時間を設定する

    // メンバー変数
    private BluetoothAdapter mBluetoothAdapter;    // BluetoothAdapter : Bluetooth処理で必要
    private String mDeviceAddress = "";    // デバイスアドレス
    private BluetoothService mBluetoothService;    // BluetoothService : Bluetoothデバイスとの通信処理を担う
    private final byte[] mReadBuffer        = new byte[READBUFFERSIZE];//受信データを一時的に格納するためのバッファー
    private int    mReadBufferCounter = 0;//通信データのバッファーカウンターを保持するための変数
    //private final OutputStream mOutputStream = null;//出力ストリーム

    // GUIアイテム
    private Button mButton_Connect;    // 接続ボタン
    private Button mButton_Disconnect;    // 切断ボタン
    private Button mButton_Start;    // センサ測定開始ボタン
    private Button mButton_End;    // センサ測定終了ボタン
    private Button mButton_Hosu;
    private Button mButton_Stand;
    private Button mButton_Step;

    // Bluetoothサービスから情報を取得するハンドラ
    //Handlerは、スレッド間でメッセージをやり取りするための仕組み
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        // ハンドルメッセージ
        // UIスレッドの処理なので、UI処理について、runOnUiThread対応は、不要。
        @Override
        public void handleMessage( Message msg ) {
            switch( msg.what ) {//メッセージの種類（msg.what）に基づいて処理を分岐
                case BluetoothService.MESSAGE_STATECHANGE://Bluetoothデバイスの接続状態に関連するメッセージの処理
                    switch( msg.arg1 ) {//Bluetoothデバイスの接続状態に応じて処理を分岐
                        case BluetoothService.STATE_NONE:            // 未接続
                            break;
                        case BluetoothService.STATE_CONNECT_START:        // 接続開始
                            break;
                        case BluetoothService.STATE_CONNECT_FAILED:            // 接続失敗
                            Toast.makeText( MainActivity.this, "Failed to connect to the device.", Toast.LENGTH_SHORT ).show();//接続に失敗した場合、トーストメッセージが表示
                            break;
                        case BluetoothService.STATE_CONNECTED:    // 接続完了
                            // GUIアイテムの有効無効の設定
                            // 切断ボタン、文字列送信ボタンmButton_Disconnectを有効にする
                            mButton_Disconnect.setEnabled( true );
                            break;
                        case BluetoothService.STATE_CONNECTION_LOST:            // 接続ロスト
                            //Toast.makeText( MainActivity.this, "Lost connection to the device.", Toast.LENGTH_SHORT ).show();
                            break;
                        case BluetoothService.STATE_DISCONNECT_START:
                            // GUIアイテムの有効無効の設定
                            // 切断ボタン、文字列送信ボタンを無効にする
                            mButton_Disconnect.setEnabled( false );
                            break;
                        case BluetoothService.STATE_DISCONNECTED:            // 切断完了
                            // GUIアイテムの有効無効の設定
                            // 接続ボタンmButton_Connectを有効にする
                            mButton_Connect.setEnabled( true );
                            mBluetoothService = null;    // BluetoothServiceオブジェクトの解放
                            break;
                    }
                    break;
                case BluetoothService.MESSAGE_READ://Bluetoothデバイスからデータを受信した際の処理

                    byte[] abyteRead = (byte[])msg.obj;//メッセージから受信データをバイト配列にキャストして取得
                    int iCountBuf = msg.arg1;//受信バイト数を取得
                    for( int i = 0; i < iCountBuf; i++ ) {//受信したデータを1バイトずつ処理
                        byte c = abyteRead[i];//受信データを1バイト取得
                        if( '\r' == c ) {    // データの終端を示す文字（'\r'）が見つかると、受信したデータが一つのメッセージとして処理
                            mReadBuffer[mReadBufferCounter] = '\0'; //文字列の終端を示すヌル文字 ('\0') を追加している
                            // GUIアイテムへの反映
                            String res_string = new String( mReadBuffer, 0, mReadBufferCounter );//受信データを文字列に変換
                            //受信したメッセージに応じて、mButton_StartボタンとmButton_Endボタンの有効無効を切り替える
                            if (mButton_Start.isEnabled() && res_string.equals("start")){
                                init_sensor(); //センサーを初期化する
                            }
                            if(mButton_End.isEnabled() && res_string.equals("end")){
                                end();
                            }
//                            ( (TextView)findViewById( R.id.textview_read ) ).setText( new String( mReadBuffer, 0, mReadBufferCounter ) );
                            ( (TextView)findViewById( R.id.textview_read ) ).setText(res_string);//受信したデータの内容をTextViewに表示
                            mReadBufferCounter = 0;//受信データの処理が終わった後、カウンターをリセット
                        }
                        else if( '\n' == c ) {//通信データが改行文字（\n）の場合、何もせずにスキップ  何もしない
                        }
                        else {    // 途中
                            if( ( READBUFFERSIZE - 1 ) > mReadBufferCounter ) {    // バッファ内に受信データを保存できる余地があるかどうかを判定,mReadBuffer[READBUFFERSIZE - 2] までOK。
                                // mReadBuffer[READBUFFERSIZE - 1] は、バッファー境界内だが、「\0」を入れられなくなるのでNG。
                                mReadBuffer[mReadBufferCounter] = c;//受信データをバッファーの次の位置に保存
                                mReadBufferCounter++;//バッファーカウンターをインクリメント
                            }
                            else {    // 初期化もしバッファーの容量に受信データが収容しきれない場合、バッファーがあふれてしまったことを意味
                                mReadBufferCounter = 0;//次回の受信データを新しいメッセージとして処理するために初期化
                            }
                        }
                    }
                    break;
            }
        }
    };


    @SuppressLint("MissingInflatedId")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2) //Androidアプリケーションで特定のAPIレベル（バージョン）以上が必要な場合に、コードが正しく実行されるためのアノテーション
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);//親クラスのonCreateメソッドを呼び出し
        setContentView(R.layout.activity_main);//アクティビティのレイアウトを設定
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);//SENSOR_SERVICEを取得して、sensorManager変数にセンサーマネージャーのインスタンスを格納
        textView = findViewById(R.id.textview);//text_viewというIDを持つTextViewを取得して、textView変数に格納


        // GUIアイテム
        mButton_Connect = findViewById( R.id.button_connect ); //ボタンに関する操作やリスナーの設定が可能になる
        mButton_Connect.setOnClickListener( this ); //thisは、アクティビティ自体がクリックリスナーを実装していることを示す
        mButton_Disconnect = findViewById( R.id.button_disconnect );
        mButton_Disconnect.setOnClickListener( this );
        mButton_Start = findViewById( R.id.start );
        mButton_Start.setOnClickListener( this );
        mButton_End = findViewById( R.id.end );
        mButton_End.setOnClickListener( this );
        mButton_End.setEnabled( false ); //mButton_Endを無効化
        mButton_Hosu = findViewById( R.id.hosu );
        mButton_Hosu.setOnClickListener( this );
        mButton_Hosu.setEnabled( false ); //mButton_Hosuを無効化
        mButton_Stand = findViewById( R.id.stand );
        mButton_Stand.setOnClickListener( this );
        mButton_Step = findViewById( R.id.step );
        mButton_Step.setOnClickListener( this );


        // Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE ); //Bluetooth関連の操作を行うためのインターフェースを取得する
        mBluetoothAdapter = bluetoothManager.getAdapter(); //BluetoothManagerインスタンスを用いて、Bluetoothアダプタを取得
        if( null == mBluetoothAdapter )
        {    // Android端末がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
            return;
        }

        Log.i(TAG, "onCreate: ");//ログメッセージを出力している
    }

    //ボタンがクリックされたときに呼び出され、クリックされたボタンに応じて適切なアクションを実行
    //各ボタンにはそれぞれ接続、切断、初期化、終了といった動作が設定
    @Override
    public void onClick( View v ) {
        if( mButton_Connect.getId() == v.getId() ) {//接続ボタンがクリックされた場合の処理
            mButton_Connect.setEnabled( false );    // 接続ボタンの無効化（連打対策）
            connect();            // 接続
            return;
        }
        if( mButton_Disconnect.getId() == v.getId() ) {//切断ボタンがクリックされた場合の処理
            mButton_Disconnect.setEnabled( false );    // 切断ボタンの無効化（連打対策）
            disconnect();            // 切断
            return;
        }
        if( mButton_Start.getId() == v.getId() ) {//開始ボタンがクリックされた場合の処理
            mButton_Start.setEnabled( false );    // ボタンの無効化（連打対策）
            init_sensor();            // 接続
            return;
        }
        if( mButton_End.getId() == v.getId() ) {//終了ボタンがクリックされた場合の処理
            mButton_End.setEnabled( false );    // ボタンの無効化（連打対策）
            end();            // 切断
        }
        if( mButton_Hosu.getId() == v.getId() ) {//歩数ボタン
            mButton_Hosu.setEnabled( false );    // ボタンの無効化（連打対策）
            mButton_Stand.setEnabled(true);
            mButton_Step.setEnabled(true);
            hosu_stand_step_select = 0;
            ( (TextView)findViewById( R.id.textView_hosu_step_stand ) ).setText( "歩数：" );
            return;

        }
        if( mButton_Stand.getId() == v.getId() ) {//立ち上がりボタン
            mButton_Stand.setEnabled( false );    // ボタンの無効化（連打対策）
            mButton_Hosu.setEnabled( true );    // ボタンの無効化（連打対策）
            mButton_Step.setEnabled(true);
            hosu_stand_step_select = 1;
            ( (TextView)findViewById( R.id.textView_hosu_step_stand ) ).setText( "立ち上がり：" );
            return;
        }
        if( mButton_Step.getId() == v.getId() ) {//ステッピングボタン
            mButton_Step.setEnabled( false );    // ボタンの無効化（連打対策）
            mButton_Hosu.setEnabled( true );    //
            mButton_Stand.setEnabled(true);
            hosu_stand_step_select = 2;
            ( (TextView)findViewById( R.id.textView_hosu_step_stand ) ).setText( "ステッピング：" );
            return;
        }
    }



    //日付情報をyyyy年MM月dd日_HH時㎜分ss秒で取得
    public static String getNowDate(){
        final DateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN);
        final Date date = new Date(System.currentTimeMillis());//現在のタイムスタンプ（ミリ秒単位の数値）を取得
        return df.format(date);//現在の日付と時刻を指定されたフォーマットの文字列として取得
    }

    protected long getNowTime(){//現在の時刻のタイムスタンプ（ミリ秒単位の数値）を取得
        return System.currentTimeMillis();//currentTimeMillis()メソッドは、システムクロックの現在時刻をミリ秒単位で返すメソッド
    }

    //アプリケーションが前面に表示される際に呼び出されるコールバックメソッド
    @Override
    protected void onResume() {
        super.onResume();

        // Android端末のBluetooth機能の有効化要求
        requestBluetoothFeature();

        // GUIアイテムの有効無効の設定
        mButton_Connect.setEnabled( false );//接続ボタン
        mButton_Disconnect.setEnabled( false );//切断ボタン

        hosu_stand_step_select = 0;
        // デバイスアドレスが空でなければ、接続ボタンを有効にする。
        if( !mDeviceAddress.equals( "" ) )//デバイスアドレスが設定されている場合、接続ボタンを有効
        {
            mButton_Connect.setEnabled( true );
        }

        // 接続ボタンを押す
        mButton_Connect.callOnClick();//mButton_Connectがクリックされたときと同じ処理が実行
        Log.i(TAG, "onResume: ");//ログ出力
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause() {
        super.onPause();//親クラス（通常はActivityクラス）のonPause()メソッドが適切に実行

        // 切断
        disconnect();
    }

    // アクティビティの終了直前
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if( null != mBluetoothService )//mBluetoothServiceはBluetoothサービスのインスタンスを示す変数
        {
            mBluetoothService.disconnect();//Bluetoothデバイスとの接続が切断される
            mBluetoothService = null;//Bluetoothサービスの参照が解放され、メモリリークを防止
        }
    }

    //start buttonを押したときの挙動
    //センサを起動する
    protected void init_sensor(){

        write("Start Sensor"); //write()メソッドが文字列を記録するか、ログに出力するなどの役割を担っている
        mButton_Start.setEnabled( false );    // ボタンの無効化
        String date1 = getNowDate(); //現在の日付と時刻を取得して、それを特定のフォーマットで文字列として返す役割を持つ
        String accelFileName = date1 + "_accel_sensor.csv"; //日付を含むファイル名を生成
        String hosu_stand_step_FileName;
//        String gyroFileName = date1 + "_gyro_sensor.csv";
//        String magnetFileName = date1 + "_magnet_sensor.csv";
//        String SMAacFileName = date1 + "_SMAacdata.csv";

        if(hosu_stand_step_select == 0){
            hosu_stand_step_FileName = date1 + "_hosu_3axis_Peak.csv";
        } else if (hosu_stand_step_select == 1) {
            hosu_stand_step_FileName = date1 + "_stand_3axis_Peak.csv";
        } else  {
            hosu_stand_step_FileName = date1 + "_step_3axis_Peak.csv";
        }
        String axisPeakFileName = hosu_stand_step_FileName;

            Context context = getApplicationContext(); //アプリケーションのコンテキストを取得
        EditText edit_v = findViewById(R.id.interval_time); //このコンポーネントにアクセスして、ユーザー入力などの操作を行うことができる
        if (!edit_v.getText().toString().equals("")) { //テキストを取得し、それが空でないかどうかを判定
            INTERVAL_TIME = Integer.parseInt(edit_v.getText().toString()); //ユーザーが入力した値がINTERVAL_TIMEの値として設定される
//        } else {
//            INTERVAL_TIME = 20; //コンポーネントにテキストが空であった場合に、デフォルトの値を設定
        }
        edit_v = findViewById(R.id.peak_stats);
        if (!edit_v.getText().toString().equals("")) {
            PEAK_NUM = Double.parseDouble(edit_v.getText().toString()); //そのテキストを浮動小数点数に変換して、PEAK_NUM 変数に代入
        }
        edit_v = findViewById(R.id.priod);
        if (!edit_v.getText().toString().equals("")) {
            PERIOD  = Double.parseDouble(edit_v.getText().toString()); //そのテキストを浮動小数点数に変換して、PERIOD 変数に代入
        }

        //加速度センサーデータの記録用
        accelFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), accelFileName); //getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) は、データの保存先を指定
//        gyrofile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), gyroFileName);
//        magnetfile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), magnetFileName);
//        SMAacFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), SMAacFileName);

        axisPeakFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), axisPeakFileName);
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); //Sensor.TYPE_ACCELEROMETER は、加速度センサーのタイプを指定
//        Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//        Sensor magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST); //センサーデータが取得された際に特定の処理が実行される
//        sensorManager.registerListener(this, gyro, 10000);
//        sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_FASTEST);
//        sensorManager.registerListener(this, magnet, 10000);
//        sensorManager.registerListener(this, magnet, SensorManager.SENSOR_DELAY_FASTEST);

        start_time = getNowTime(); //開始時刻を取得
        now_time = 0;
        accel_data.init();
        gyro_data.init();
        magnet_data.init();
        passed_time = new ArrayList<>(); //時間の経過を記録するためのリストとして使用されるもの
        mButton_End.setEnabled( true );    // ボタンの有効化
    }

    //end buttonを押したときの挙動
    //センサを終了させる
    //取得データをファイルに出力する
    protected  void end(){
        mButton_End.setEnabled( false );    // ボタンの有効化
        sensorManager.unregisterListener(this);
        saveFile();
        passed_time = null;
        mButton_Start.setEnabled( true );    // ボタンの有効化
        write("Stop Sensor");
    }

    //センサの値が変更されるごとに呼ばれるコールバック関数
    //センサの値を取得するとともに画面に出力する
    @Override
    public void onSensorChanged(SensorEvent event) {
        float sensorX;
        float sensorY;
        float sensorZ;
        long sensorT; //センサーから取得したデータのタイムスタンプを格納するためのもの

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            now_time = getNowTime();
            sensorX = event.values[0];
            sensorY = event.values[1];
            sensorZ = event.values[2];
            sensorT = now_time == 0 ? 0 : now_time - start_time;


            if(passed_time.size() != 0) { //加速度センサーデータの処理の条件を示す
                long passed_time_latest = passed_time.get(passed_time.size()-1); //リストの最後の要素を取得して、passed_time_latest 変数に格納、直前の処理時点での経過時間を表す
                if (abs(accel_data.time.get(accel_data.time.size() - 1) - passed_time_latest) < abs(sensorT - passed_time_latest) ) { //経過時間とセンサーデータのタイムスタンプを比較し、データの整合性を確認
                    accel_data.x.add(sensorX); //データを更新
                    accel_data.y.add(sensorY);
                    accel_data.z.add(sensorZ);
                    passed_time.add(passed_time.get(passed_time.size()-1) + INTERVAL_TIME); //一定の時間間隔ごとに経過時間を記録し、データ処理の整合性を保つためのもの
                    accel_data.time.add(sensorT);
                }else{ //データの整合性が保たれていない場合
                    //データの整合性が崩れた場合に、データを直前の値に戻すことで整合性を保つための処理
                    accel_data.x.set(accel_data.x.size()-1,sensorX);
                    accel_data.y.set(accel_data.y.size()-1,sensorY);
                    accel_data.z.set(accel_data.z.size()-1,sensorZ);
                    accel_data.time.set(accel_data.time.size()-1,sensorT);
                }
            }else {//リストが空の場合
                accel_data.x.add(sensorX); //新しいデータを追加
                accel_data.y.add(sensorY);
                accel_data.z.add(sensorZ);
                int first_time_stack = (int)sensorT/INTERVAL_TIME; //何回目のセンサーデータかを求める
                first_time_stack = first_time_stack == 0 ? 1 : first_time_stack; //0 の場合、1 に置き換える
                int first_time = first_time_stack * INTERVAL_TIME; //最初の経過時間を計算している
                if((int)sensorT % first_time >= INTERVAL_TIME/2){ //最初の経過時間を設定する際に、センサーデータのタイムスタンプがその経過時間の中間よりも後であるかどうかを判断している
                    passed_time.add((long) ((first_time_stack + 1) * INTERVAL_TIME)); //中間よりも後である場合、次の経過時間は first_time_stack + 1 に INTERVAL_TIME を掛けた値
                }else{
                    passed_time.add((long) first_time); //中間よりも前である場合、次の経過時間は first_time_stack に INTERVAL_TIME を掛けた値
                }
                accel_data.time.add(sensorT);
            }
            // showInfo(event);
        }

//        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
//            now_time = getNowTime();
//            sensorX = event.values[0];
//            sensorY = event.values[1];
//            sensorZ = event.values[2];
//            sensorT = now_time == 0 ? 0 : now_time - start_time;
//
//            gyro_data.x.add(sensorX);
//            gyro_data.y.add(sensorY);
//            gyro_data.z.add(sensorZ);
//            gyro_data.time.add(sensorT);
//        }
//
//        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
//            now_time = getNowTime();
//            sensorX = event.values[0];
//            sensorY = event.values[1];
//            sensorZ = event.values[2];
//            sensorT = now_time == 0 ? 0 : now_time - start_time;
//
//            magnet_data.x.add(sensorX);
//            magnet_data.y.add(sensorY);
//            magnet_data.z.add(sensorZ);
//            magnet_data.time.add(sensorT);
//        }

        String strTmp = "";
        if (accel_data.time.size() > 0) {
            strTmp += "経過時間(ms) : " + accel_data.time.get(accel_data.time.size() - 1) + "\n" //最新の加速度センサーデータの経過時間をミリ秒単位で表示
                    + "加速度センサ\n"
                    + " X: " + accel_data.x.get(accel_data.x.size() - 1) + "\n"
                    + " Y: " + accel_data.y.get(accel_data.y.size() - 1) + "\n"
                    + " Z: " + accel_data.z.get(accel_data.z.size() - 1) + "\n";
        }
//        if (gyro_data.time.size() > 0) {
//            strTmp += "ジャイロセンサ\n"
//                    + " X: " + gyro_data.x.get(gyro_data.x.size() - 1) + "\n"
//                    + " Y: " + gyro_data.y.get(gyro_data.y.size() - 1) + "\n"
//                    + " Z: " + gyro_data.z.get(gyro_data.z.size() - 1) + "\n";
//        }
//        if (magnet_data.time.size() > 0) {
//            strTmp += "地磁気センサ\n"
//                    + " X: " + magnet_data.x.get(magnet_data.x.size() - 1) + "\n"
//                    + " Y: " + magnet_data.y.get(magnet_data.y.size() - 1) + "\n"
//                    + " Z: " + magnet_data.z.get(magnet_data.z.size() - 1) + "\n";
//        }
        textView.setText(strTmp);
//        Log.i(TAG, "onSensorChanged: ");
        //立ち上がり用
        if(hosu_stand_step_select == 1) {
            //　タイマーが10秒経過するとタイマーをストップする
            if (accel_data.time.get(accel_data.time.size() - 1) >= STAND_TIME) {
                mButton_End.setEnabled(false);    // ボタンの有効化
                sensorManager.unregisterListener(this);
                saveFile();
                passed_time = null;
                mButton_Start.setEnabled(true);    // ボタンの有効化
                write("Stop Sensor");

                //　サウンド出力
                // MediaPlayer のインスタンス生成
                MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.testtone);
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.start();

                // MediaPlayerの解放
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.release();
                    }
                });
            }
        }else if(hosu_stand_step_select == 2){ //ステッピング用
            //　タイマーが10秒経過するとタイマーをストップする
            if (accel_data.time.get(accel_data.time.size() - 1) >= STEP_TIME) {
                mButton_End.setEnabled(false);    // ボタンの有効化
                sensorManager.unregisterListener(this);
                saveFile();
                passed_time = null;
                mButton_Start.setEnabled(true);    // ボタンの有効化
                write("Stop Sensor");

                //サウンド出力MediaPlayer のインスタンス生成
                MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.testtone);
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.start();

                // MediaPlayerの解放
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.release();
                    }
                });
            }
        }

    }



    //未使用　定義のみ
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {//Androidのセンサーの精度が変化したとき

    }

    // 以下センサ関連メソッド
    // ファイルを保存
    public void saveFile() {
        try {
            if (isExternalStorageWritable()) { //外部ストレージが書き込み可能な場合
                String str;
                if (accel_data.time.size() > 0) { // リストが空でない場合
                    FileOutputStream output = new FileOutputStream(accelFile, true);
                    ArrayList<Double> comDataList = new ArrayList<>(); //三次元の加速度データをユークリッドノルム（三次元ベクトルの大きさ）で計算してリストに追加
                    for (int i = 0; i < passed_time.size(); i++) { //センサーデータの数と同じだけの加速度データが処理される
                        comDataList.add(Math.sqrt(Math.pow(accel_data.x.get(i), 2) + Math.pow(accel_data.y.get(i), 2) + Math.pow(accel_data.z.get(i), 2)));
                    }
                    str = "計測時間,実際の時間,X軸,Y軸,Z軸,3軸\n";
                    output.write(str.getBytes());
                    for (int i = 0; i < passed_time.size(); i++) {
                        str = passed_time.get(i) + "," + accel_data.time.get(i) + "," + accel_data.x.get(i) + "," + accel_data.y.get(i) + "," + accel_data.z.get(i) + "," + comDataList.get(i) + "\n";
                        output.write(str.getBytes());
                    }
                    output.flush();
                    output.close();

                    // 新しいコード（3軸合成加速度 ピーク検出）を追加
                    // axisPeakFile というファイルにデータを書き込むための FileOutputStream オブジェクトを作成
                    // true フラグを使用して、ファイルが既存の内容を保持しながらデータを追加するように設定
                    output = new FileOutputStream(axisPeakFile, true); // 既存のファイルにデータを追加していくことができる
                    ArrayList<String> comDataPeakList = new ArrayList<>(); // ピーク検出の結果や関連する情報を格納するための ArrayList オブジェクト comDataPeakList を作成
                    ArrayList<String> comDataUnderPeakList = new ArrayList<>();
                    ArrayList<Integer> pointsBetweenPeaks = new ArrayList<>(); // 条件に合致するピーク間のデータポイント数を格納
                    int period = (int) PERIOD;
                    int lowPeriod = (int) LOW_PERIOD;
                    int firstPeak = 0;
                    int lowPeak = 0;
                    int countdown = period;
                    int lowCountdown = lowPeriod;
                    float MaxPEAK_NUM = 0;
                    int peakCount = 0; // ピークのカウントを初期化
                    int lowCount = 0;
                    int lastPeakIndex = -1;//最後の上ピーク

                    // ユークリッドノルムデータをfloat配列に変換
                    // omDataList 内の各要素を float 型に変換し、新しい acc 配列に格納
                    float[] acc = new float[comDataList.size()];
                    for (int i = 0; i < comDataList.size(); i++) {
                        acc[i] = comDataList.get(i).floatValue();
                        if(MaxPEAK_NUM < comDataList.get(i).floatValue()){
                           MaxPEAK_NUM = comDataList.get(i).floatValue();
                        }
                    }
                    //int[] isCandidate = new int[acc.length];
                    for (int i = 0; i < comDataList.size() - 1; i++) {
                        if  (hosu_stand_step_select == 1 ) {
                            //valが閾値を超えている、かつflag 変数は現在のデータポイントがピーク候補であるかどうかを示すフラグ
                            if (acc[i] < LOW_PEAK_NUM && lowPeak == 0 && acc[i - 1] > acc[i] && acc[i + 1] > acc[i]) {
                                // ピークが検出されたのでカウントを増やす
                                lowCount++;
                                comDataPeakList.add(String.valueOf(comDataList.get(i)));
                                lowPeak = 1;
                                lowCountdown = lowPeriod;
                            } else {
                                comDataPeakList.add("");
                            }

                            if (lowPeak == 1 && lowCountdown == 0) {
                                lowPeak = 0;
                            }

                            if (lowPeak == 1) {
                                lowCountdown--;

                            } else {
                                comDataUnderPeakList.add("");
                            }
                        }else {//上ピーク検出処理
                            if (acc[i] > PEAK_NUM && firstPeak == 0 && acc[i - 1] < acc[i] && acc[i + 1] < acc[i]) {
                                // ピークが検出された場合
                                peakCount++;//カウントを増やす
                                comDataPeakList.add(String.valueOf(comDataList.get(i)));
                                firstPeak = 1;

                                if (lastPeakIndex != -1) {
                                    int pointCount = 0;
                                    for (int j = lastPeakIndex + 1; j < i; j++) {
                                        if (acc[j] < 9.8) {
                                            pointCount++;
                                        }
                                    }
                                    pointsBetweenPeaks.add(pointCount);
                                }
                                lastPeakIndex = i;
                            }
                        }
                            for (int count : pointsBetweenPeaks) {
                                System.out.println("ピーク間のデーター数" + count);

//                            } else {
//                                comDataPeakList.add("");
//                            }

                            if (firstPeak == 1 && countdown == 0) {
                                firstPeak = 0;
                                countdown = period;
                            }

                            if (firstPeak == 1) {
                                countdown--;
                            }
                        }


                    }
//                    int period = 6;
//                    int firstPeak = 0;
//                    int countdown = period;
//                    int candidateIndex = -1;
//                    float candidateValue = Float.MIN_VALUE;
//                    int peakCount = 0; // ピークのカウントを初期化
//                    // ユークリッドノルムデータをfloat配列に変換
//                    // omDataList 内の各要素を float 型に変換し、新しい acc 配列に格納
//                    float[] acc = new float[comDataList.size()];
//                    for (int i = 0; i < comDataList.size(); i++) {
//                        acc[i] = comDataList.get(i).floatValue();
//                    }
//
//                    int[] isCandidate = new int[acc.length];
//
//                    //Map<Integer, Float> peaks = new HashMap<>();
//
//                    //ピーク候補を示すフラグが格納された isCandidate 配列.この配列は、データポイントごとにピーク候補かどうかを示す整数値を持っており、ピーク検出の対象データ
//                    //各データポイントに対して、countdown のカウントダウン、flag の評価、および val の値の取得
//                    for (int i = 0; i < isCandidate.length; i++) {
//                        //int flag = acc[i];//flag 変数は、現在のデータポイントがピーク候補であるかどうかを示すフラグ。isCandidate 配列の要素から値を取得して設定
//                        //float val = acc[i];//val 変数は、現在のデータポイントの値（加速度データなど）を示す。acc 配列の要素から値を取得して設定
//
//                        //valが閾値を超えている、かつflag 変数は現在のデータポイントがピーク候補であるかどうかを示すフラグ
//                        if (acc[i] > PEAK_NUM && firstPeak == 0) {
//                            // ピークが検出されたのでカウントを増やす
//                            peakCount++;
//                            comDataPeakList.add(String.valueOf(comDataList.get(i)));
//                            firstPeak = 1;
//
//                        }else{
//                            comDataPeakList.add("");
//                        }
//
//                        if(firstPeak == 1 && countdown == 0 ) {
//                            firstPeak = 0;
//                            countdown = period;
//                        }
//
//                        if(firstPeak == 1){
//                            countdown--;
//                        }
//                    }

//
//                        //int flag = acc[i];//flag 変数は、現在のデータポイントがピーク候補であるかどうかを示すフラグ。isCandidate 配列の要素から値を取得して設定
//                        //float val = acc[i];//val 変数は、現在のデータポイントの値（加速度データなど）を示す。acc 配列の要素から値を取得して設定
//
//                        //valが閾値を超えている、かつflag 変数は現在のデータポイントがピーク候補であるかどうかを示すフラグ
//                        if (acc[i] > PEAK_NUM && candidateIndex == -1 ) {
//                            first_peak  ;
//                            candidateIndex = i;
//                            candidateValue = acc[i];
//                            countdown = period;
//                        }
//                        isCandidate[i] = 0; // デフォルトではピーク候補ではないと設定
//
//                        //countdown が0になるか、データポイントが最後に達した場合、現在のピーク候補が確定され、その位置と値が peaks マップに追加
//                        if (acc[i] > PEAK_NUM && (countdown == 0 || i == isCandidate.length - 1 )) {
//                            if (candidateIndex != -1) {
//                                peaks.put(candidateIndex, candidateValue);
//                                candidateIndex = -1;
//                                // ピークが検出されたのでカウントを増やす
//                                peakCount++;
//                                comDataPeakList.add(String.valueOf(comDataList.get(i)));
//                            } else {
//                                comDataPeakList.add("");
//                            }
//                            //countdown は period の値にリセットされ、新しいピーク候補の有効期間が開始
//                            countdown = period;
//                        } else {
//                            comDataPeakList.add("");
//                        }
//                    }
//                    for(int i=delay_time; i<comDataList.size()-1; i++){ //遅延時間以降の加速度データの要素に対してループ
//                        if (comDataList.get(i-1) < comDataList.get(i) && comDataList.get(i) > comDataList.get(i+1)){ //加速度データがピークの条件を満たしているかを判定
//                            if (comDataList.get(i) > PEAK_NUM) { //ピークの条件を満たす加速度データの場合、その値が一定の閾値 PEAK_NUM を超えているかを確認
//                                if(get_peak==0 || get_peak + peak_get_deley_time/INTERVAL_TIME < i ) { //t_peak が 0 であるか、一定の時間間隔（peak_get_deley_time）が経過した場合、新しいピークが検出されたと判断
//                                    comDataPeakList.add(String.valueOf(comDataList.get(i))); //現在のピークの値を追加し、get_peak を現在のインデックス i に更新
//                                    get_peak = i;
//                                    before_peak = comDataList.get(i); //現在のピークの値で更新
//                                }
//                                else{ //つまり同じピーク範囲内である場合
//                                    if(comDataList.get(i) > before_peak){ //ピークの値が before_peak よりも大きければ、現在のピークで前回のピーク情報をクリアし、新しいピーク情報を comDataPeakList に追加
//                                        comDataPeakList.set(get_peak,"");
//                                        comDataPeakList.add(String.valueOf(comDataList.get(i)));
//                                    }else { //ピーク条件を満たさないため、空の情報を comDataPeakList に追加
//                                        comDataPeakList.add("");
//                                    }
//                                }
//                        } else {
//                            comDataPeakList.add("");
//                        }
//                    }
////                    //ピーク情報を含むデータをファイルに保存する処理
//                    comDataPeakList.add(""); //comDataPeakList に空の情報を追加
//                    for (int i = 0; i < comDataPeakList.size(); i++) { //歩数の計算に使用されており、ピーク情報が空の場合（歩行のピークがない場合）に歩数をカウント
//                        if (comDataPeakList.get(i).equals("")) {
//                            peakCount++;
//                            char[] peak_num_hosu = new char[0];
//                            ((TextView) findViewById(R.id.textview_hosu)).setText(String.valueOf(peak_num_hosu));
//
                    if(hosu_stand_step_select == 0){
                        str = "歩数," + peakCount + "\n";
                        ((TextView) findViewById(R.id.textView_hosu)).setText(String.valueOf(peakCount));
                    } else if(hosu_stand_step_select == 1){
                        if(lowCount != 0){
                            str = "立ち上がり数," + lowCount + "\n";
                            ((TextView) findViewById(R.id.MaxPeak)).setText(String.valueOf(MaxPEAK_NUM));
                            ((TextView) findViewById(R.id.textView_hosu)).setText(String.valueOf(lowCount));
                        } else {
                            str = "立ち上がり数, 0\n";
                            ((TextView) findViewById(R.id.textView_hosu)).setText("0");
                        }
                    } else if(hosu_stand_step_select == 2){
                        if(peakCount != 0){
                            str = "ステップ数," + peakCount/2 + "\n";
                            ((TextView) findViewById(R.id.textView_hosu)).setText(String.valueOf(peakCount/2));
                        } else {
                            str = "ステップ数, 0\n";
                            ((TextView) findViewById(R.id.textView_hosu)).setText("0");
                        }
                    }

                    str += "計測時間,実時間,3軸,ピーク\n"; //計測時間、実時間、3軸のデータ、ピーク情報を表すヘッダー行を作成し、str 変数に追加
                    output.write(str.getBytes()); //ファイルに書き込み
                    for (int i = 0; i < passed_time.size(); i++) { //各要素を組み合わせて1行のデータを作成し、ファイルに書き込み
                        str = passed_time.get(i) + "," + accel_data.time.get(i) + "," + comDataList.get(i) + "," + comDataPeakList.get(i) + "\n";
                        output.write(str.getBytes());
                    }
                    output.flush();
                    output.close();
                }

//移動平均フィルタ
//                    ArrayList<Double> comDataSMAList = new ArrayList<>();
//                output = new FileOutputStream(SMAacFile, true);
//                str = "計測時間,実時間,X軸,Y軸,Z軸\n";
//                output.write(str.getBytes());
//                    for(int i = 0; i < passed_time.size() - SMA_NUM ; i++){
//                        double SMADataAve_x = 0;
//                        double SMADataAve_y = 0;
//                        double SMADataAve_z = 0;
//                        for(int j = 0; j < SMA_NUM; j++){
//                            SMADataAve_x += accel_data.x.get(i + j);
//                            SMADataAve_y += accel_data.y.get(i + j);
//                            SMADataAve_z += accel_data.z.get(i + j);
//                        }
//                        SMAac_data.x.add((float) (SMADataAve_x/ SMA_NUM));
//                        SMAac_data.y.add((float) (SMADataAve_y/ SMA_NUM));
//                        SMAac_data.z.add((float) (SMADataAve_z/ SMA_NUM));
//                    }
//                    for (int i = 0; i < passed_time.size() -SMA_NUM; i++) {
//                        str = passed_time.get(i) + "," + accel_data.time.get(i) + "," + SMAac_data.x.get(i) + "," + SMAac_data.y.get(i) + "," + SMAac_data.z.get(i) + "\n";
//                        output.write(str.getBytes());
//                    }
//                    output.flush();
//                    output.close();


                //角速度センサ
//                if (gyro_data.time.size() > 0) {
//                    FileOutputStream output = new FileOutputStream(gyrofile, true);
//                    str = "測定時間,実際の時間,X軸(角速度),Y軸(角速度),Z軸(角速度)\n";
//                    output.write(str.getBytes());
//                    int gyro_first_stack = (int)(gyro_data.time.get(0)/INTERVAL_TIME);
//                    int gyro_passtime_stack = gyro_first_stack != 0 ? gyro_first_stack : 1;
//                    int gyro_passtime = gyro_passtime_stack * INTERVAL_TIME;
//                    int gyro_value;
//                    for (int i = 1; i < gyro_data.time.size(); i++) {
//                        if(gyro_passtime < gyro_data.time.get(i)) {
//                            if (Math.abs(gyro_passtime - gyro_data.time.get(i - 1)) > Math.abs(gyro_passtime - gyro_data.time.get(i))) {
//                                gyro_value = i;
//                            } else {
//                                gyro_value = i - 1;
//                            }
//                            str = (gyro_passtime) + ","
//                                    + gyro_data.time.get(gyro_value) + ","
//                                    + gyro_data.x.get(gyro_value) + ","
//                                    + gyro_data.y.get(gyro_value) + ","
//                                    + gyro_data.z.get(gyro_value) + "\n";
//                            output.write(str.getBytes());
//                            gyro_passtime_stack += 1;
//                            gyro_passtime = gyro_passtime_stack * INTERVAL_TIME;
//                        }
//                    }
//                    output.flush();
//                    output.close();
//                }

                //地磁気センサ
//                if (magnet_data.time.size() > 0) {
//                    FileOutputStream output = new FileOutputStream(magnetfile, true);
//                    str = "測定時間,実際の時間,X軸(地磁気強度),Y軸(地磁気強度),Z軸(地磁気強度)\n";
//                    output.write(str.getBytes());
//                    int magnet_first_stack = (int)(magnet_data.time.get(0)/INTERVAL_TIME);
//                    int magnet_passtime_stack = magnet_first_stack != 0 ? magnet_first_stack : 1;
//                    int magnet_passtime = magnet_passtime_stack * INTERVAL_TIME;
//                    int magnet_value;
//                    for (int i = 1; i < magnet_data.time.size(); i++) {
//                        if(magnet_passtime < magnet_data.time.get(i)) {
//                            if (Math.abs(magnet_passtime - magnet_data.time.get(i - 1)) > Math.abs(magnet_passtime - magnet_data.time.get(i))) {
//                                magnet_value = i;
//                            } else {
//                                magnet_value = i - 1;
//                            }
//                            str = (magnet_passtime) + ","
//                                    + magnet_data.time.get(magnet_value) + ","
//                                    + magnet_data.x.get(magnet_value) + ","
//                                    + magnet_data.y.get(magnet_value) + ","
//                                    + magnet_data.z.get(magnet_value) + "\n";
//                            output.write(str.getBytes());
//                            magnet_passtime_stack += 1;
//                            magnet_passtime = magnet_passtime_stack * INTERVAL_TIME;
//                        }
//                    }
//                    output.flush();
//                    output.close();
//                }

            }
        }catch (IOException e) { //もし何らかのエラーが発生した場合、IOException がキャッチされ、エラーの詳細情報が出力
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
            Log.e(TAG, "saveFile: Failed to write to file", e);
        }
        Log.i(TAG, "saveFile: Successful writing to file");//成功メッセージをログに出力
    }
    public boolean isExternalStorageWritable(){ //外部ストレージの状態を取得
        String state = Environment.getExternalStorageState(); //取得した状態を文字列で state 変数に格納
        return(Environment.MEDIA_MOUNTED.equals(state)); //Environment.MEDIA_MOUNTED と取得した状態が一致するかどうかを確認し、外部ストレージが書き込み可能な状態であれば true を返す
    }

    /* Checks if external storage is available for read and write */
    //外部ストレージが書き込み可能な状態かどうかを確認するため



    // 以下bluetooth関連メソッド
    //Bluetooth通信を行うためのBluetoothServiceクラスの定義
    static public class BluetoothService {
        // 定数（Bluetooth UUID） Bluetoothデバイス間でシリアル通信を行うために使用
        private static final UUID UUID_SPP = UUID.fromString( "00001101-0000-1000-8000-00805f9b34fb" );

        // : Bluetooth通信の状態を表す定数
        public static final int MESSAGE_STATECHANGE    = 1;
        public static final int MESSAGE_READ           = 2;
        public static final int MESSAGE_WRITTEN        = 3;
        public static final int STATE_NONE             = 0;
        public static final int STATE_CONNECT_START    = 1; //接続開始
        public static final int STATE_CONNECT_FAILED   = 2;
        public static final int STATE_CONNECTED        = 3; //接続完了
        public static final int STATE_CONNECTION_LOST  = 4;
        public static final int STATE_DISCONNECT_START = 5;
        public static final int STATE_DISCONNECTED     = 6;


        // メンバー変数
        private int              mState; //通信の進行状況や結果を表すため
        private ConnectionThread mConnectionThread; //ConnectionThreadクラスのインスタンスがここで保持
        private final Handler mHandler; //HandlerはUIスレッドとバックグラウンドスレッド間でメッセージの受け渡しを行うために使用

        // 接続時処理用のスレッド
        private class ConnectionThread extends Thread {
            private BluetoothSocket mBluetoothSocket; // Bluetoothデバイスとの通信を行うためのBluetoothSocket（Bluetoothの接続ポイント）を表す変数
            private InputStream mInput; //Bluetoothデバイスからのデータを受信するためのInputStream（入力ストリーム）を表す変数
            private OutputStream mOutput; //Bluetoothデバイスにデータを送信するためのOutputStream（出力ストリーム）を表す変数

            // コンストラクタ
            public ConnectionThread( BluetoothDevice bluetoothdevice ) { //bluetoothdevice: Bluetoothデバイスへの接続を行うためのBluetoothDeviceオブジェクト
                Log.d("BluetoothService", "connect to: " + bluetoothdevice);
                try {//mBluetoothSocket: 指定したBluetoothデバイスとの通信を行うためのBluetoothSocketを作成
                    mBluetoothSocket = bluetoothdevice.createRfcommSocketToServiceRecord( UUID_SPP );  //指定されたUUID（UUID_SPP）に基づいてBluetoothSocketを生成
                    mInput = mBluetoothSocket.getInputStream(); //getInputStreamメソッドを使用してBluetoothSocketからInputStreamを取得
                    mOutput = mBluetoothSocket.getOutputStream(); //getOutputStreamメソッドを使用してBluetoothSocketからOutputStreamを取得
                    mBluetoothSocket.getOutputStream();
                }
                catch( IOException e ) {
                    Log.e( "BluetoothService", "failed : bluetoothdevice.createRfcommSocketToServiceRecord( UUID_SPP )", e );
                }
            }

            // 処理
            //ConnectionThreadスレッドが実行される際に呼び出されるメソッド
            public void run() {
                while( STATE_DISCONNECTED != mState ) { //mStateがSTATE_DISCONNECTEDになるまで接続処理を続ける
                    switch( mState ) {
                        case STATE_NONE:
                            break;
                        case STATE_CONNECT_START:    // 接続開始
                            try {
                                // BluetoothSocketオブジェクトを用いて、Bluetoothデバイスに接続を試みる。
                                mBluetoothSocket.connect();
                            }
                            catch( IOException e ) {    // 接続失敗
                                Log.e("BluetoothService", String.valueOf(e));
                                Log.d( "BluetoothService", "Failed : mBluetoothSocket.connect()" );
                                setState( STATE_CONNECT_FAILED );
                                cancel();    // スレッド終了。
                                return;
                            }
                            // 接続成功
                            setState( STATE_CONNECTED );
                            break;
                        case STATE_CONNECT_FAILED:        // 接続失敗
                            // 接続失敗時の処理の実体は、cancel()。
                            break;
                        case STATE_CONNECTED:        // 接続済み（Bluetoothデバイスから送信されるデータ受信）
                            byte[] buf = new byte[1024];
                            int bytes;
                            try {
                                bytes = mInput.read( buf );
                                mHandler.obtainMessage( MESSAGE_READ, bytes, -1, buf ).sendToTarget();
                            }
                            catch( IOException e ) {
                                setState( STATE_CONNECTION_LOST );
                                cancel();    // スレッド終了。
                                break;
                            }
                            break;
                        case STATE_CONNECTION_LOST:    // 接続ロスト
                            // 接続ロスト時の処理の実体は、cancel()。
                            break;
                        case STATE_DISCONNECT_START:    // 切断開始
                            // 切断開始時の処理の実体は、cancel()。
                            break;
                        //case STATE_DISCONNECTED:    // 切断完了
                        // whileの条件式により、STATE_DISCONNECTEDの場合は、whileを抜けるので、このケース分岐は無意味。
                        //break;
                    }
                }
                synchronized( BluetoothService.this ) {    // 親クラスが保持する自スレッドオブジェクトの解放（自分自身の解放）
                    mConnectionThread = null;
                }
            }

            // キャンセル（接続を終了する。ステータスをSTATE_DISCONNECTEDにすることによってスレッドも終了する）
            public void cancel() { //Bluetoothデバイスとの接続を終了するために使用
                try {
                    mBluetoothSocket.close(); //mBluetoothSocketを使用してBluetoothデバイスとの接続を閉じる
                }
                catch( IOException e ) {
                    Log.e( "BluetoothService", "Failed : mBluetoothSocket.close()", e );
                }
                setState( STATE_DISCONNECTED ); //STATE_DISCONNECTEDに変更します。これにより、スレッドが終了するか、別の処理に遷移する際にスレッドの状態を更新
            }

            // バイト列送信
            //指定されたバイト配列をBluetoothデバイスに書き込むために使用
            public void write( byte[] buf ) {
                try {
                    synchronized( BluetoothService.this ) { //複数のスレッドが同時に write メソッドにアクセスする場合に、競合状態を避けるための同期化
                        mOutput.write( buf ); //mOutput を使用して、指定されたバイト配列 buf をBluetoothデバイスに書き込み
                    }
                    mHandler.obtainMessage( MESSAGE_WRITTEN ).sendToTarget(); //Bluetoothデバイスへの書き込みが完了したことを通知
                }
                catch( IOException e ) { //書き込み処理中に発生する可能性のあるIO例外をキャッチ
                    Log.e( "BluetoothService", "Failed : mBluetoothSocket.close()", e );
                }
            }
        }

        // コンストラクタ
        public BluetoothService(Handler handler, BluetoothDevice device) {
            mHandler = handler; //Bluetooth通信の状態やデータ送受信の情報をメインスレッドに通知できる
            mState = STATE_NONE; //Bluetooth通信の状態を初期化

            // 接続時処理用スレッドの作成と開始
            mConnectionThread = new ConnectionThread( device ); //Bluetoothデバイスとの接続処理を行うスレッドが作成
            mConnectionThread.start(); //Bluetoothデバイスとの接続を試みる処理が実行
        }

        // ステータス設定
        private synchronized void setState( int state ) {
            mState = state; //Bluetooth通信の現在の状態を表す値が更新
            mHandler.obtainMessage( MESSAGE_STATECHANGE, state, -1 ).sendToTarget(); //メインスレッドに対して、Bluetooth通信の状態変更を通知するためのメッセージを作成し、メインスレッドの Handler に送信
        }

        // 接続開始時の処理
        public synchronized void connect() {
            if( STATE_NONE != mState ) {    // １つのBluetoothServiceオブジェクトに対して、connect()は１回だけ呼べる。
                // ２回目以降の呼び出しは、処理しない。
                return; //メソッドを終了して接続処理を行わない
            }

            // ステータス設定
            setState( STATE_CONNECT_START ); //接続処理の開始が通知され、接続処理は ConnectionThread 内で行われるため、この状態設定により該当するスレッドが接続処理を開始するようになる
        }

        // 接続切断時の処理
        public synchronized void disconnect() {
            if( STATE_CONNECTED != mState ) {    // 接続中以外は、処理しない。
                return;//メソッドを終了して切断処理を行わない
            }

            // ステータス設定
            setState( STATE_DISCONNECT_START ); //切断処理の開始が通知

            mConnectionThread.cancel(); //Bluetoothデバイスとの接続が切断
        }
        // バイト列送信
        public void write( byte[] out ) { // Bluetoothデバイスにバイト列を送信するためのメソッド
            ConnectionThread connectionThread; //ConnectionThread クラスのインスタンスを格納する変数を宣言
            synchronized( this ) { //のブロック内での処理は同時に実行されない
                if( STATE_CONNECTED != mState ) { // 接続中以外は、処理しない。
                    return;
                }
                connectionThread = mConnectionThread; //connectionThread の値が一貫していることを保証
            }
            connectionThread.write( out );
        }
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu ) { //アクティビティのメニューアイテムを作成し、アクションバーに表示するためのメソッド
        getMenuInflater().inflate( R.menu.activity_main, menu ); //getMenuInflater() メソッドを使用して、メニューのインフレート
        return true; //メニューの作成が成功した場合、true を返して処理が成功したことを示す
    }

    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item ) { //メニューアイテムが選択されたときに呼び出されるメソッド
        int itemId = item.getItemId();// 選択されたメニューアイテムのIDを取得
        if (itemId == R.id.menuitem_search) { //IDが R.id.menuitem_search と一致する場合、特定の処理を実行
            Intent devicelistactivityIntent = new Intent(this, DeviceListActivity.class); //DeviceListActivity を起動するための Intent を作成
            startActivityForResult(devicelistactivityIntent, REQUEST_CONNECTDEVICE); //DeviceListActivity を起動し、デバイスを選択して戻ってくる結果を待つ
            return true;
        }
        return false; // メニューアイテムの処理が指定された条件に合致しない場合、false を返す
    }

    // 機能の有効化ダイアログの操作結果
    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data ) { //他のアクティビティからの結果を受け取るためのメソッド
        switch( requestCode ) { //questCode を使用して、どのアクティビティからの結果かを判断
            case REQUEST_ENABLEBLUETOOTH: // Bluetooth有効化要求
                if( Activity.RESULT_CANCELED == resultCode ) {    // 有効にされなかった
                    Toast.makeText( this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT ).show(); //メッセージを表示
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
            case REQUEST_CONNECTDEVICE: // デバイス接続要求
                String strDeviceName;
                if( Activity.RESULT_OK == resultCode ) {
                    // デバイスリストアクティビティからの情報の取得
                    strDeviceName = data.getStringExtra( DeviceListActivity.EXTRAS_DEVICE_NAME ); //: デバイスの名前を取得
                    mDeviceAddress = data.getStringExtra( DeviceListActivity.EXTRAS_DEVICE_ADDRESS ); //デバイスのアドレスを取得
                }
                else {
                    strDeviceName = "";
                    mDeviceAddress = "";
                }
                ( (TextView)findViewById( R.id.textview_devicename ) ).setText( strDeviceName ); //デバイスの名前を表示する TextView に、取得したデバイス名をセット
                ( (TextView)findViewById( R.id.textview_deviceaddress ) ).setText( mDeviceAddress ); //デバイスのアドレスを表示する TextView に、取得したデバイスアドレスをセット
                ( (TextView)findViewById( R.id.textview_read ) ).setText( "" ); // テキストをクリアするため、表示する TextView の内容を空に設定
                break;
        }
        super.onActivityResult( requestCode, resultCode, data ); //親クラスの onActivityResult() メソッドを呼び出す
    }

    // Android端末のBluetooth機能の有効化要求
    private void requestBluetoothFeature() {
        if( mBluetoothAdapter.isEnabled() ) { //既にBluetoothが有効な場合は、何もせずに関数を終了
            return;
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE ); //要求のためのリクエストコード
        startActivityForResult( enableBtIntent, REQUEST_ENABLEBLUETOOTH );
    }

    // 接続
    private void connect() {
        if( mDeviceAddress.equals( "" ) ) {    // DeviceAddressが空の場合は処理しない
            return;
        }

        if( null != mBluetoothService ) {    // mBluetoothServiceがnullでないなら接続済みか、接続中。
            return;
        }

        // 接続
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice( mDeviceAddress ); //mDeviceAddress からBluetoothデバイスを取得
        mBluetoothService = new BluetoothService(mHandler, device ); //BluetoothService クラスのインスタンスを作成し、mBluetoothService に格納
        mBluetoothService.connect(); //Bluetoothデバイスへの接続を開始
    }

    // 切断
    private void disconnect() {
        if( null == mBluetoothService ) {    // mBluetoothServiceがnullなら切断済みか、切断中。
            return;
        }

        // 切断
        mBluetoothService.disconnect(); //disconnect() メソッドを呼び出して、Bluetoothデバイスとの接続を切断
        mBluetoothService = null; //mBluetoothService を null に設定して、接続を保持するインスタンスを破棄
    }
    // 文字列送信
    //Bluetoothデバイスにデータを送信するための write() メソッド
    private void write( String string )
    {
        if( null == mBluetoothService )
        {    // mBluetoothServiceがnullなら切断済みか、切断中。
            return;
        }

        // 終端に改行コードを付加
        String stringSend = string + "\r\n"; //送信する文字列に改行コード（\r\n）を追加して、送信する文字列を生成

        // バイト列送信
        mBluetoothService.write( stringSend.getBytes() );//送信する文字列は getBytes() メソッドを通じてバイト配列に変換されて送信される
    }


}