package com.example.sensor_test;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
//AppCompatActivityを継承することで、Androidアプリの基本的な機能をサポートしている
//AdapterView.OnItemClickListenerインターフェースはリストアイテムがクリックされたときのイベントを処理するためのメソッド（onItemClick）を提供する
public class DeviceListActivity extends AppCompatActivity implements AdapterView.OnItemClickListener{
    static class DeviceListAdapter extends BaseAdapter//BaseAdapterを継承することで、リストビューにデータを提供し、それをカスタマイズして表示する方法を提供
    {
        private final ArrayList<BluetoothDevice> mDeviceList;//DeviceListAdapterクラス内で使用されるBluetoothデバイスのリストを保持するためのプライベート変数
        private final LayoutInflater mInflator;//DeviceListAdapterクラス内で使用されるレイアウトインフレーターを保持するためのプライベート変数
        //DeviceListAdapterの新しいインスタンスを作成
        //内部のmDeviceListとmInflatorを初期化
        public DeviceListAdapter( Activity activity )
        {
            super();//親クラスのコンストラクターを呼び出す
            mDeviceList = new ArrayList<>();
            mInflator = activity.getLayoutInflater();
        }

        // Bluetoothデバイスをリストに追加するためのもの
        public void addDevice( BluetoothDevice device )
        {
            if( !mDeviceList.contains( device ) )
            {    // 加えられていなければ加える
                mDeviceList.add( device );
                notifyDataSetChanged();    // ListViewの更新:データの変更を通知し、表示を更新
            }
        }

        // リストのクリア
        public void clear()
        {
            mDeviceList.clear();
            notifyDataSetChanged();    // ListViewの更新:データの変更を通知し、表示を更新
        }

        @Override//mDeviceListリストの要素数（Bluetoothデバイスの数）を返す。
        public int getCount()
        {
            return mDeviceList.size();
        }

        @Override//mDeviceListリストから指定された位置のBluetoothデバイスを取得して返す
        public Object getItem( int position )
        {
            return mDeviceList.get( position );
        }

        @Override//指定された位置（position）にあるデータアイテムのIDを取得します
        public long getItemId( int position )
        {
            return position;
        }

        //リストのアイテムを再利用する際に、ビュー要素の検索や参照の作成を避けて効率的に表示を行うことができる
        static class ViewHolder
        {
            TextView deviceName;//Bluetoothデバイスの名前を表示する
            TextView deviceAddress;//Bluetoothデバイスのアドレスを表示する
        }

        //getViewメソッドは、リストビューの各アイテムを表示する
        //アイテムの表示に使用されるビューを取得または作成し、アイテムのデータを表示するためにビューに情報をセット
        @Override
        public View getView(int position, View convertView, ViewGroup parent )
        {
            ViewHolder viewHolder;
            // General ListView optimization code.:一般的なListViewの最適化コード
            //リストアイテムのレイアウトをインフレートして新しいビューを作成し、ViewHolderを作成してビューの中のTextViewへの参照を保持
            if( null == convertView )
            // convertViewがnullの場合、ビューが初めて作成されることを意味
            // リストアイテムのレイアウトをインフレートしてViewHolderを初期化
            {
                convertView = mInflator.inflate( R.layout.listitem_device, parent, false );
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = convertView.findViewById( R.id.textview_deviceaddress );
                viewHolder.deviceName = convertView.findViewById( R.id.textview_devicename );

                // ViewHolderをconvertViewのタグに設定して、後で取得できるようにす。
                convertView.setTag( viewHolder );
            }
            else//アイテムが再利用される場合（スクロールなど）、前回のconvertViewを再利用して新しいアイテムのデータを表示する
            {
                // convertViewがnullでない場合、ビューが再利用されていることを意味
                // 以前に作成されたViewHolderを取得
                viewHolder = (ViewHolder)convertView.getTag();
            }
            // ここで、ViewHolderにはTextViewへの参照が含まれている
            // リストのこの特定の位置に対応するデータをセットすることができる

            // 指定された位置のBluetoothDeviceオブジェクトを取得
            BluetoothDevice device     = mDeviceList.get( position );//BluetoothDeviceリストで指定された位置のデバイスの名前とアドレスを取得
            String          deviceName = device.getName();
            if( null != deviceName && 0 < deviceName.length() )
            {
                viewHolder.deviceName.setText( deviceName );
            }
            else
            {
                viewHolder.deviceName.setText( R.string.unknown_device );
            }
            // デバイス名とアドレスをViewHolder内の対応するTextViewに設定
            viewHolder.deviceAddress.setText( device.getAddress() );

            // 更新されたconvertViewを返して、データが表示されたリストアイテムを表す
            return convertView;
        }
    }

    // 定数
    private static final int    REQUEST_ENABLEBLUETOOTH = 1; // Bluetooth機能の有効化要求時の識別コード
    public static final  String EXTRAS_DEVICE_NAME      = "DEVICE_NAME";//Bluetoothデバイスの名前をIntentに格納する際のキーとして使用,Bluetoothデバイスの名前をアプリの別のアクティビティに渡す必要がある場合
    public static final  String EXTRAS_DEVICE_ADDRESS   = "DEVICE_ADDRESS";//Bluetoothデバイスのアドレス（一意の識別子）をIntentに格納する際のキー,Bluetoothデバイスのアドレスをアプリの別のアクティビティに渡す必要がある場合

    // メンバー変数
    private BluetoothAdapter mBluetoothAdapter;        // BluetoothAdapter : Bluetooth処理で必要
    private DeviceListAdapter mDeviceListAdapter;    // リストビューの内容
    private boolean mScanning = false;                // スキャン中かどうかのフラグ

    // ブロードキャストレシーバー：他のアプリやシステムが発行したブロードキャストインテントを受信し、特定のアクションに応じて処理を実行するための仕組
    //ブロードキャストレシーバーのonReceiveメソッドをオーバーライドして処理を定義することができる
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent )
        {
            String action = intent.getAction();//受信したインテントからアクション（Action）を取得

            // Bluetooth端末発見
            if( BluetoothDevice.ACTION_FOUND.equals( action ) )//Bluetoothデバイスが検出されたことを示すアクション文字列
            {
                final BluetoothDevice device = intent.getParcelableExtra( BluetoothDevice.EXTRA_DEVICE );//インテントからBluetoothデバイスオブジェクトを取得
                runOnUiThread(() -> mDeviceListAdapter.addDevice( device ));//UIスレッドでmDeviceListAdapter（Bluetoothデバイスのリストアダプター）にデバイスを追加する処理を実行
                return;
            }
            // Bluetooth端末検索終了
            if( BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals( action ) )//Bluetoothデバイスの検索が完了したことを示すアクション文字列
            {
                runOnUiThread(() -> {
                    mScanning = false;//変数をfalseに設定して、スキャンが終了したことを示す
                    // メニューの更新
                    invalidateOptionsMenu();
                });
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)//このアクティビティがJelly Bean MR2 (Android 4.3) 以降で動作することを示す
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );//親クラスのonCreateメソッドを呼び出し,アクティビティの初期化
        setContentView( R.layout.activity_device_list );//activity_device_listというレイアウトをアクティビティにセット

        // アクティビティの戻り値をRESULT_CANCELEDに設定
        setResult( Activity.RESULT_CANCELED );

        // リストビューの設定
        mDeviceListAdapter = new DeviceListAdapter( this ); // ビューアダプターの初期化
        ListView listView = findViewById( R.id.devicelist );    // リストビューの取得
        listView.setAdapter( mDeviceListAdapter );    // リストビューにビューアダプターをセット
        listView.setOnItemClickListener( this ); // リストビューのアイテムがクリックされた際に処理を行うクリックリスナーオブジェクトのセット

        // Bluetoothアダプタの取得\
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService( Context.BLUETOOTH_SERVICE );//BluetoothManagerを取得し、Bluetoothを制御するためのサービスを取得
        mBluetoothAdapter = bluetoothManager.getAdapter();//Bluetoothアダプターを取得し、mBluetoothAdapter変数に格納
        if( null == mBluetoothAdapter )
        {    // デバイス（＝スマホ）がBluetoothをサポートしていない
            Toast.makeText( this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT ).show();
            finish();    // アプリ終了宣言
        }
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();//親クラスのonResumeメソッドを呼び出し

        // デバイスのBluetooth機能の有効化要求
        requestBluetoothFeature();

        // ブロードキャストレシーバーの登録
        registerReceiver( mBroadcastReceiver, new IntentFilter( BluetoothDevice.ACTION_FOUND ) );//Bluetoothデバイスが発見されたときにmBroadcastReceiverのonReceiveメソッドが呼び出される
        registerReceiver( mBroadcastReceiver, new IntentFilter( BluetoothAdapter.ACTION_DISCOVERY_FINISHED ) );//Bluetoothデバイスの検索が終了したときにmBroadcastReceiverのonReceiveメソッドが呼び出される

        // スキャン開始
        startScan();
    }

    // 別のアクティビティ（か別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause()
    {
        super.onPause();//アクティビティの一時停止時に実行される処理を実行

        // スキャンの停止
        stopScan();

        // ブロードキャストレシーバーの登録解除
        unregisterReceiver( mBroadcastReceiver );
    }

    //デバイスのBluetooth機能が有効になっているかどうかを確認し、無効な場合は有効化するダイアログを表示するメソッド
    // デバイスのBluetooth機能の有効化要求
    private void requestBluetoothFeature()
    {
        //Bluetooth機能が有効になっているかを確認
        //isEnabled()は、Bluetoothアダプターが有効かどうかを示すメソッド
        if( mBluetoothAdapter.isEnabled() )
        {
            return;//Bluetooth機能が有効な場合は、メソッドを終了して戻る
        }
        // デバイスのBluetooth機能が有効になっていないときは、有効化要求（ダイアログ表示）
        Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        startActivityForResult( enableBtIntent, REQUEST_ENABLEBLUETOOTH );
    }

    // 機能の有効化ダイアログの操作結果
    //onActivityResultメソッドは、他のアクティビティから戻ってきた結果を受け取るために使用されるメソッド
    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data )
    {
        if (requestCode == REQUEST_ENABLEBLUETOOTH) { // Bluetooth有効化要求
            if (Activity.RESULT_CANCELED == resultCode) {    // リクエストがキャンセルされたかどうか
                Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT).show();//Bluetoothの有効化が拒否されたことをユーザーに通知するためのトーストメッセージを表示
                finish();    // アプリ終了宣言
                return;
            }
        }
        super.onActivityResult( requestCode, resultCode, data );
    }

    // スキャンの開始
    private void startScan()//Bluetoothデバイスのスキャンを開始する際に呼び出される
    {
        // リストビューの内容を空にする。
        mDeviceListAdapter.clear();

        // スキャンの開始
        mScanning = true;
        mBluetoothAdapter.startDiscovery();	// 約 12 秒間の問い合わせのスキャンが行われる

        // メニューの更新
        invalidateOptionsMenu();
    }

    // スキャンの停止
    private void stopScan()//Bluetoothデバイスのスキャンを停止する際に呼び出される
    {
        // スキャンの停止
        mBluetoothAdapter.cancelDiscovery();
    }

    // リストビューのアイテムクリック時の処理
    //onItemClickメソッドは、リストビューの項目がクリックされたときに呼び出されるメソッド
    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id )
    {
        // クリックされたアイテムの取得
        BluetoothDevice device = (BluetoothDevice)mDeviceListAdapter.getItem( position );
        if( null == device )//Bluetoothデバイスの情報が取得できない場合の処理
        {
            return;
        }
        // 戻り値の設定
        Intent intent = new Intent();//新しいIntentオブジェクトを作成
        intent.putExtra( EXTRAS_DEVICE_NAME, device.getName() );//Bluetoothデバイスの名前（デバイス名）をEXTRAS_DEVICE_NAMEキーでIntentに追加
        intent.putExtra( EXTRAS_DEVICE_ADDRESS, device.getAddress() );//Bluetoothデバイスのアドレス（MACアドレス）をEXTRAS_DEVICE_ADDRESSキーでIntentに追加
        setResult( Activity.RESULT_OK, intent );//アクティビティの結果をRESULT_OK（成功）に設定し、返す結果のIntentを設定
        finish();
    }

    // オプションメニュー作成時の処理
    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.activity_device_list, menu );//R.menu.activity_device_listで定義されたメニューリソースを使用して、アクションバーメニューを作成
        if( !mScanning )//mScanningフラグがfalseの場合、スキャンが行われていない状態を示す
        {
            menu.findItem( R.id.menuitem_stop ).setVisible( false );//スキャンを停止するメニューアイテムを非表示
            menu.findItem( R.id.menuitem_scan ).setVisible( true );//スキャンを開始するメニューアイテムを表示
            menu.findItem( R.id.menuitem_progress ).setActionView( null );//進行状況を示すプログレスバー（アニメーション）を非表示
        }
        else//mScanningフラグがtrueの場合、スキャンが行われている状態を示す
        {
            menu.findItem( R.id.menuitem_stop ).setVisible( true );//スキャンを停止するメニューアイテムを表示
            menu.findItem( R.id.menuitem_scan ).setVisible( false );//スキャンを開始するメニューアイテムを非表示
            menu.findItem( R.id.menuitem_progress ).setActionView( R.layout.actionbar_indeterminate_progress );//進行状況を示すプログレスバー（アニメーション）を表示
        }
        return true;//メニューを作成した場合はtrueを返す,これにより、メニューが表示
    }

    // オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch( item.getItemId() )//選択されたアクションバーメニューのアイテムIDを取得して、それに応じて処理を分岐
        {
            case R.id.menuitem_scan:
                startScan();    // スキャンの開始
                break;
            case R.id.menuitem_stop:
                stopScan();    // スキャンの停止
                break;
        }
        return true;
    }

}