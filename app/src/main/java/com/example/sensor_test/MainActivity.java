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

    //センサ値クラス
    //getter setter なし
    //センサーの値を保持するためのSensorDataClassという内部クラス
    //センサーのx軸、y軸、z軸の値を保持するためのArrayList<Float>
    class SensorDataClass {
        ArrayList<Float> x;
        ArrayList<Float> y;
        ArrayList<Float> z;
        //センサーの各軸の値を取得した時刻を保持するためのArrayList<Long>
        ArrayList<Long> time;

        SensorDataClass() {
            //センサーの値を初期化するために呼び出され,各リストを空にして再初期化
            init();
        }

        public void init() {
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
    ArrayList<Long> passed_time;//センサーデータの取得間隔を記録するためのリスト

    long start_time;//センサーデータの取得を開始した時刻を記録するための変数
    long now_time;
    File accelFile;//加速度データのファイル
    File axisPeakFile;//3軸加速度ピークデータのファイルを表す
    String TAG = "Main";//デバッグログに表示する際のタグ名を示すための変数


    // 定数
    private int INTERVAL_TIME = 20; //測定間隔(mSec)
    private double PEAK_NUM = 11; //ピーク検出用閾値(水平に手に持った時) 自然に持って振ったときは14.0前後必要
    private double PERIOD = 6;//一つ目のピークから次のピークまでの検出しない時間を設定する
    private double STEP_TIME = 10000;//ステッピングの計測時間を設定する

    // GUIアイテム
    private Button mButton_Start;    // センサ測定開始ボタンa
    private Button mButton_End;    // センサ測定終了ボタン

    @SuppressLint("MissingInflatedId")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    //Androidアプリケーションで特定のAPIレベル（バージョン）以上が必要な場合に、コードが正しく実行されるためのアノテーション
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);//親クラスのonCreateメソッドを呼び出し
        setContentView(R.layout.activity_main);//アクティビティのレイアウトを設定
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);//SENSOR_SERVICEを取得して、sensorManager変数にセンサーマネージャーのインスタンスを格納
        textView = findViewById(R.id.textview);//text_viewというIDを持つTextViewを取得して、textView変数に格納


        // GUIアイテム
        mButton_Start = findViewById(R.id.start);
        mButton_Start.setOnClickListener(this);
        mButton_End = findViewById(R.id.stop);
        mButton_End.setOnClickListener(this);
        mButton_End.setEnabled(false); //mButton_Endを無効化
        Log.i(TAG, "onCreate: ");//ログメッセージを出力している
    }

    //ボタンがクリックされたときに呼び出され、クリックされたボタンに応じて適切なアクションを実行
    //各ボタンにはそれぞれ接続、切断、初期化、終了といった動作が設定
    @Override
    public void onClick(View v) {
        if( mButton_Start.getId() == v.getId() ) {//開始ボタンがクリックされた場合の処理
            mButton_Start.setEnabled( false );    // ボタンの無効化（連打対策）
            init_sensor();            // 接続
            return;
        }
        if( mButton_End.getId() == v.getId() ) {//終了ボタンがクリックされた場合の処理
            mButton_End.setEnabled( false );    // ボタンの無効化（連打対策）
            end();            // 切断
        }
    }


    //日付情報をyyyy年MM月dd日_HH時㎜分ss秒で取得
    public static String getNowDate() {
        final DateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN);
        final Date date = new Date(System.currentTimeMillis());//現在のタイムスタンプ（ミリ秒単位の数値）を取得
        return df.format(date);//現在の日付と時刻を指定されたフォーマットの文字列として取得
    }

    protected long getNowTime() {//現在の時刻のタイムスタンプ（ミリ秒単位の数値）を取得
        return System.currentTimeMillis();//currentTimeMillis()メソッドは、システムクロックの現在時刻をミリ秒単位で返すメソッド
    }

    // アクティビティの終了直前
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //start buttonを押したときの挙動
    //センサを起動する
    protected void init_sensor() {

        mButton_Start.setEnabled(false);    // ボタンの無効化
        String date1 = getNowDate(); //現在の日付と時刻を取得して、それを特定のフォーマットで文字列として返す役割を持つ
        String accelFileName = date1 + "_accel_sensor.csv"; //日付を含むファイル名を生成

        String hosu_stand_step_FileName = date1 + "_step_3axis_Peak.csv";
        String axisPeakFileName = hosu_stand_step_FileName;

        Context context = getApplicationContext(); //アプリケーションのコンテキストを取得

        //加速度センサーデータの記録用
        accelFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), accelFileName); //getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) は、データの保存先を指定

        axisPeakFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), axisPeakFileName);
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); //Sensor.TYPE_ACCELEROMETER は、加速度センサーのタイプを指定
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST); //センサーデータが取得された際に特定の処理が実行される


        start_time = getNowTime(); //開始時刻を取得
        now_time = 0;
        accel_data.init();
        gyro_data.init();
        magnet_data.init();
        passed_time = new ArrayList<>(); //時間の経過を記録するためのリストとして使用されるもの
        mButton_End.setEnabled(true);    // ボタンの有効化
    }

    //end buttonを押したときの挙動
    //センサを終了させる
    //取得データをファイルに出力する
    protected void end() {
        mButton_End.setEnabled(false);    // ボタンの有効化
        sensorManager.unregisterListener(this);
        saveFile();
        passed_time = null;
        mButton_Start.setEnabled(true);    // ボタンの有効化
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


            if (passed_time.size() != 0) { //加速度センサーデータの処理の条件を示す
                long passed_time_latest = passed_time.get(passed_time.size() - 1); //リストの最後の要素を取得して、passed_time_latest 変数に格納、直前の処理時点での経過時間を表す
                if (abs(accel_data.time.get(accel_data.time.size() - 1) - passed_time_latest) < abs(sensorT - passed_time_latest)) { //経過時間とセンサーデータのタイムスタンプを比較し、データの整合性を確認
                    accel_data.x.add(sensorX); //データを更新
                    accel_data.y.add(sensorY);
                    accel_data.z.add(sensorZ);
                    passed_time.add(passed_time.get(passed_time.size() - 1) + INTERVAL_TIME); //一定の時間間隔ごとに経過時間を記録し、データ処理の整合性を保つためのもの
                    accel_data.time.add(sensorT);
                } else { //データの整合性が保たれていない場合
                    //データの整合性が崩れた場合に、データを直前の値に戻すことで整合性を保つための処理
                    accel_data.x.set(accel_data.x.size() - 1, sensorX);
                    accel_data.y.set(accel_data.y.size() - 1, sensorY);
                    accel_data.z.set(accel_data.z.size() - 1, sensorZ);
                    accel_data.time.set(accel_data.time.size() - 1, sensorT);
                }
            } else {//リストが空の場合
                accel_data.x.add(sensorX); //新しいデータを追加
                accel_data.y.add(sensorY);
                accel_data.z.add(sensorZ);
                int first_time_stack = (int) sensorT / INTERVAL_TIME; //何回目のセンサーデータかを求める
                first_time_stack = first_time_stack == 0 ? 1 : first_time_stack; //0 の場合、1 に置き換える
                int first_time = first_time_stack * INTERVAL_TIME; //最初の経過時間を計算している
                if ((int) sensorT % first_time >= INTERVAL_TIME / 2) { //最初の経過時間を設定する際に、センサーデータのタイムスタンプがその経過時間の中間よりも後であるかどうかを判断している
                    passed_time.add((long) ((first_time_stack + 1) * INTERVAL_TIME)); //中間よりも後である場合、次の経過時間は first_time_stack + 1 に INTERVAL_TIME を掛けた値
                } else {
                    passed_time.add((long) first_time); //中間よりも前である場合、次の経過時間は first_time_stack に INTERVAL_TIME を掛けた値
                }
                accel_data.time.add(sensorT);
            }
            // showInfo(event);
        }


        String strTmp = "";
        if (accel_data.time.size() > 0) {
            strTmp += "経過時間(ms) : " + accel_data.time.get(accel_data.time.size() - 1) + "\n" //最新の加速度センサーデータの経過時間をミリ秒単位で表示
                    + "加速度センサ\n"
                    + " X: " + accel_data.x.get(accel_data.x.size() - 1) + "\n"
                    + " Y: " + accel_data.y.get(accel_data.y.size() - 1) + "\n"
                    + " Z: " + accel_data.z.get(accel_data.z.size() - 1) + "\n";
        }

        textView.setText(strTmp);
        //　タイマーが10秒経過するとタイマーをストップする
        if (accel_data.time.get(accel_data.time.size() - 1) >= STEP_TIME) {
            mButton_End.setEnabled(false);    // ボタンの有効化
            sensorManager.unregisterListener(this);
            saveFile();
            passed_time = null;
            mButton_Start.setEnabled(true);    // ボタンの有効化

            //サウンド出力MediaPlayer のインスタンス生成
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.testtone);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.start();

            // MediaPlayerの解放
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                }
            });
        }

    }

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
                    ArrayList<Integer> pointsBetweenPeaks = new ArrayList<>();//ピーク間のデータ数を格納するリスト
                    ArrayList<String> comDataLowPeakList = new ArrayList<>();//下ピークのリスト

                    int period = (int) PERIOD;
                    int countdown = 0;
                    int peakCount = 0; // ピークのカウントを初期化
                    int firstPeak = 0;
                    int lastPeakIndex = -1;
                    double average = 0;//ピーク間のデータ数の平均
                    double standardDeviation = 0;//ピーク間のデータ数の標準偏差
                    boolean isSearchingForLowPeak = false;//下ピークを探しているか示すフラグ
                    boolean foundLowPeak = false;//最小値が取れているか
                    double minPeak = Double.MAX_VALUE;//下のピークを初期化
                    int lowIndex = -1;//最小値のインデックス
                    double lowAverage = 0;//下ピーク間の平均

                    // ユークリッドノルムデータをfloat配列に変換
                    // omDataList 内の各要素を float 型に変換し、新しい acc 配列に格納
                    float[] acc = new float[comDataList.size()];
                    for (int i = 0; i < comDataList.size(); i++) {
                        acc[i] = comDataList.get(i).floatValue();
                    }
                    int[] isCandidate = new int[acc.length];

                    //ピーク候補を示すフラグが格納された isCandidate 配列.この配列は、データポイントごとにピーク候補かどうかを示す整数値を持っており、ピーク検出の対象データ
                    //各データポイントに対して、countdown のカウントダウン、flag の評価、および val の値の取得
                    for (int i = 0; i < isCandidate.length; i++) {
                        if (isSearchingForLowPeak && acc[i] < minPeak) {
                            // 下のピークを探している場合、最小値を更新
                            minPeak = acc[i];
                            lowIndex = i;
                        }

                        if (acc[i] > PEAK_NUM && firstPeak == 0 && acc[i - 1] < acc[i] && acc[i + 1] < acc[i]) {
                            // 上のピークが検出された場合
                            peakCount++;
                            comDataPeakList.add(String.valueOf(comDataList.get(i)));
                            firstPeak = 1;

                            if (lastPeakIndex != -1) {
                                int pointCount = 0; // ピーク間のデータ数を初期化
                                for (int j = lastPeakIndex + 1; j < i; j++) {
                                    if (acc[j] < 9.8) {
                                        pointCount++;
                                    }
                                }
                                pointsBetweenPeaks.add(pointCount);
                            }
                            //下ピークを探す処理
                            if (isSearchingForLowPeak) {
                                // 二つの上のピーク間の最小値を下のピークとして記録
                                comDataLowPeakList.add(String.valueOf(comDataList.get(lowIndex)));
                                isSearchingForLowPeak = false;
                                minPeak = Double.MAX_VALUE;
                            } else {
                                isSearchingForLowPeak = true;
                                comDataLowPeakList.add("");
                            }
                            lastPeakIndex = i;
                        } else {
                            comDataPeakList.add("");
                            comDataLowPeakList.add("");
                        }
                        if (firstPeak == 1 && countdown == 0) {
                            firstPeak = 0;
                            countdown = period;
                        }
                        if (firstPeak == 1) {
                            countdown--;
                        }
                    }
                    if (isSearchingForLowPeak) {
                        comDataLowPeakList.add(String.valueOf(comDataList.get(lowIndex)));
                    }
                    if (!comDataLowPeakList.isEmpty()) {
                        double lowPeakSum = 0.0;
                        for (String lowValue : comDataLowPeakList) {
                            if (!lowValue.equals("") && !lowValue.equals("-1")) {
                                int index = (int) Float.parseFloat(lowValue);
                                if (index >= 0 && index < acc.length) {
                                    lowPeakSum += 9.8 - acc[index];
                                }
                            }
                        }
                        lowAverage = lowPeakSum / comDataLowPeakList.size();
                        // 画面に平均と標準偏差を表示
                        ((TextView) findViewById(R.id.textView_lowstep)).setText("下ピーク値平均: " + lowAverage);
                    }

                        if (!pointsBetweenPeaks.isEmpty()) {
                        double sum = 0.0;
                        for (int count : pointsBetweenPeaks) {
                            sum += count;
                        }
                        //データ数の平均
                        average = sum / pointsBetweenPeaks.size();

                        double standardDeviationSum = 0.0;
                        for (int count : pointsBetweenPeaks) {//各データポイントと平均値との差の二乗を計算し、その合計を求める
                            standardDeviationSum += Math.pow(count - average, 2);
                        }
                        //データ数の標準偏差
                        standardDeviation = Math.sqrt(standardDeviationSum / pointsBetweenPeaks.size());//分散の合計をデータポイントの数で割って分散を得る、その値の平方根で取ることで標準偏差を出す

                        // 画面に平均と標準偏差を表示
                        ((TextView) findViewById(R.id.textView_average)).setText("平均: " + average);
                        ((TextView) findViewById(R.id.textView_stdDev)).setText("標準偏差: " + standardDeviation);
                    } else {
                        ((TextView) findViewById(R.id.textView_average)).setText("平均: データなし");
                        ((TextView) findViewById(R.id.textView_stdDev)).setText("標準偏差: データなし");
                    }

                    if (peakCount != 0) {
                        str = "step: " + peakCount + "\n";
                        str += "step_ave : " + average + "\n";
                        str += "step_std : " + standardDeviation + "\n";
                        str += "low_step : " + lowAverage + "\n";
                        ((TextView) findViewById(R.id.textView_step)).setText("歩数: " + String.valueOf(peakCount));
                    } else {
                        str = "ステップ数, 0\n";
                        ((TextView) findViewById(R.id.textView_step)).setText("0");
                    }

                    str += "計測時間,実時間,3軸,ピーク\n"; //計測時間、実時間、3軸のデータ、ピーク情報を表すヘッダー行を作成し、str 変数に追加
                    output.write(str.getBytes()); //ファイルに書き込み
                    for (int i = 0; i < passed_time.size(); i++) {
                        String peakData = i < comDataPeakList.size() ? comDataPeakList.get(i) : "";
                        String lowPeakData = i < comDataLowPeakList.size() ? comDataLowPeakList.get(i).toString() : "";
                        str = passed_time.get(i) + "," + accel_data.time.get(i) + "," + comDataList.get(i) + "," + peakData + "," + lowPeakData + "\n";
                        output.write(str.getBytes());
                    }
                    output.flush();
                    output.close();
                }
            }
        } catch (IOException e) { //もし何らかのエラーが発生した場合、IOException がキャッチされ、エラーの詳細情報が出力
            // TODO 自動生成された catch ブロック
            e.printStackTrace();
            Log.e(TAG, "saveFile: Failed to write to file", e);
        }
        Log.i(TAG, "saveFile: Successful writing to file");//成功メッセージをログに出力
    }

    public boolean isExternalStorageWritable() { //外部ストレージの状態を取得
        String state = Environment.getExternalStorageState(); //取得した状態を文字列で state 変数に格納
        return (Environment.MEDIA_MOUNTED.equals(state)); //Environment.MEDIA_MOUNTED と取得した状態が一致するかどうかを確認し、外部ストレージが書き込み可能な状態であれば true を返す
    }
}