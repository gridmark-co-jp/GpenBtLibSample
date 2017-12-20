/*
【NOTE】
・ライブラリファイル「GpenBtLib.jar」を「...GpenBtLibSample\app\libs」に追加してからビルドしてください。
・Androidの「設定→アプリ→GPenBtLibSample(本サンプルアプリケーション)→許可」にて位置情報をONにしてください。
・使用するG-PenBTはAndroidの「設定→Bluetooth」にて事前にペアリングしておいてください。

・本アプリケーションの操作方法
    手順１："SCAN"ボタンをタップしてGPenBTを取得
    手順２："CONNECT"ボタンをタップしてGPenBTを接続
    手順３：GPenBTでドットコードをタッチして情報を取得（→※ドットコード対応する各種機能を実装して実行させる）
    手順４："DISCONNECT"ボタンをタップしてGPenBTの接続を解除
*/
package mydomain.jp.co.gpenbtlibsample;

import android.bluetooth.BluetoothDevice;
import android.graphics.Point;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.UUID;

import gridmark.jp.co.gpenbtlibrary.GPenBtLib;
import gridmark.jp.co.gpenbtlibrary.GPenBtListener;
import gridmark.jp.co.gpenbtlibrary.GPenBtNotify;

public class MainActivity extends AppCompatActivity implements GPenBtListener{

    // ----定数--------------------------------------------------
    // GPenBTの固有値(GPenBlueならば全個体が共通の値を持つパラメータ)
    final private ParcelUuid DEVICE_UUID = new ParcelUuid(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")); // デバイスのUUID

    // GPenBT１本１本異なる値（この値は適宜変更すること）
    final private String DEVICE_NAME = "GMSPP01-D098";  // 取得したいデバイスの名前
    final private String DEVICE_ADDRESS = "00:11:67:50:D0:98";  // 取得したいデバイスのアドレス

    // 変数保存キー
    final private static String SAVE_TEXT_INFO = "SAVE_TEXT_INFO";
    final private static String SAVE_TEXT_DATA = "SAVE_TEXT_DATA";

    // ----変数--------------------------------------------------
    // クラス
    private Common mCommon; // 環境や状況に依存しない単純処理をまとめたクラス
    private GPenBtLib mGPenBtLib;   // ライブラリクラス

    // BTデバイス関連
    private BluetoothDevice mBluetoothDevice;   // 接続したいBTデバイス
    private ParcelUuid mBluetoothDeviceUuid;    // 接続したいBTデバイスのUUID

    // コンポーネント
    private TextView mTextViewInfo; // 画面上のテキストビュー(上)
    private TextView mTextViewData; // 画面上のテキストビュー(下)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 変数初期化
        mCommon = new Common();
        mGPenBtLib = new GPenBtLib(this.getApplication(), new GPenBtNotify(), this);    // ライブラリクラス初期化
        mTextViewInfo = (TextView)findViewById(R.id.textView1);
        mTextViewData = (TextView)findViewById(R.id.textView2);

        SetText("", 0);
        SetText("", 1);

        // button1 をタップしたときのイベント
        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GetBtDevices(); // 手順１：GPenBTを取得
            }
        });

        // button2 をタップしたときのイベント
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ConnectBtDevice();  // 手順２：GPenBTを接続
            }
        });

        // button3 をタップしたときのイベント
        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DisConnectBtDevice();   // 手順３：GPenBTの接続を解除
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        mGPenBtLib.FromOnPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Activity破棄時（主に画面回転時）に表示中のtextView1&2文字列を保存
        outState.putString(SAVE_TEXT_INFO, mTextViewInfo.getText().toString());
        if(mTextViewData.getVisibility() == View.VISIBLE)
            outState.putString(SAVE_TEXT_DATA, mTextViewData.getText().toString());
        else
            outState.putString(SAVE_TEXT_DATA, "");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // 保存しておいたtextView1&2文字列を戻す
        SetText(savedInstanceState.getString(SAVE_TEXT_INFO), 0);
        SetText(savedInstanceState.getString(SAVE_TEXT_DATA), 1);
    }

    // mGPenBtLib.DisConnectComplated()が完了したときのイベント
    public void DisConnectComplated(){
        SetText("BTデバイスの接続を解除しました", 0);
        SetText("", 1);
        mTextViewData.setVisibility(View.INVISIBLE);
    }

    // GPenBtが発信するバイトデータを受け取るイベント
    public void RecieveGpenData(byte[] data){
        // 接続開始処理を実行した直後
        if(mTextViewInfo.getText().toString().endsWith("を接続中...")) {
            SetText("BTデバイスの接続が完了しました。\r\nドットコードをタッチしてください。", 0);   // ここに到達したということはBTペンの接続が成功したということ
            SetText("", 1);
        }

        // 受信データ解析
        if(data != null){
            // 変数初期化
            int dotcode = -1;   // アクティブコード（32bit表記 ただし先頭2bitはパリティビット）
            Point xy = new Point(-1, -1);   // XYコード
            StringBuilder output = new StringBuilder(); // 画面上に表示する文字列
            int penId = -1;     // ペンID
            int button = -1;    // ボタン
            int pattern = -1;   // パターンコード
            int rotation = -1;  // 角度

            if(data.length == 20){
                if(data[0] == (byte)0xFF && data[1] == (byte)0xFD) {
                    // 解析OK
                    if(mCommon.ByteIndex(data[2], 7)) {
                        penId = data[15] & 0xFF;
                        button = data[12];  // 0x01:三角ボタンorペン先スイッチ, 0x02:四角ボタン, 0x04:丸ボタン
                        pattern = (data[4] & 0xFF) + ((data[3] & 0xFF) << 8);
                        rotation = (data[11] & 0xFF) * 2;
                        // Dotcode(data:5,6,7,8)
                        if(data[5] >= 0){
                            if(data[5] >= 64 && data[5] <= 127)
                                data[5] -= 0x40;    // パリティ削除
                        }else{
                            if(data[5] < -64)
                                data[5] += 0x80;    // パリティ削除
                            else
                                data[5] += 0x40;    // パリティ削除
                        }
                        dotcode = ((data[5] & 0xFF) << (8 * 3)) + ((data[6] & 0xFF) << (8 * 2)) + ((data[7] & 0xFF) << (8 * 1)) + ((data[8] & 0xFF) << (8 * 0));
                        if (pattern == 0x7F01)
                            dotcode &= 0x0FFFFFFF;  // 10bitXY紙面対策

                        // Activeコード
                        if(!mCommon.ByteIndex(data[2], 3)) {
                            output.append("penId=" + penId + ", button=" + button + ", pattern=" + pattern + ", dotcode = " + dotcode + ", rotation=" + rotation + ", ");
                            SetText(output.toString(), 1);
                            // 前回取得したドットコードと異なる
                            if(!mCommon.ByteIndex(data[2], 4)) {
                                DispatchActiveCode(dotcode);
                            }
                        }
                        // XYコード
                        else{
                            int xBits = (data[14] >> 4) & 0x0F;
                            int yBits = data[14] & 0x0F;
                            // アクティブコード
                            int active = dotcode >> (xBits + yBits);
                            // XY座標
                            int bitPrecision = (data[13] & 0xFF);
                            byte xFractional = data[9];
                            byte yFractional = data[10];
                            int xMask = (1 << xBits) - 1;
                            int yMask = (1 << yBits) - 1;
                            int x = (dotcode >> yBits) & xMask;
                            int y = dotcode & yMask;
                            x <<= bitPrecision;
                            x += xFractional;
                            y <<= bitPrecision;
                            y += yFractional;
                            output.append("penId=" + penId + ", button=" + button + ", pattern=" + pattern + ", active = " + active + ", x=" + x + ", y=" + y + ", rotation=" + rotation + ", ");
                            SetText(output.toString(), 1);
                        }
                    }
                    // 解析NG
                    else{
                        // Pen up
                        if((data[2] & 0xFF) == 0x38) {   // 0 0111 000 -> 解析失敗 + ペンアップ
                            output.append("pen up");
                            SetText(output.toString(), 1);
                        }
                    }
                }
            }
        }
    }

    // ライブラリ側で例外が発生したときのイベント
    public void RecieveExceptionMessage(String msg){
        Log.d("TAG", "例外を検知しました message = " + msg);
    }

    // ----ファンクション--------------------------------------------------
    // テキストビューに文字列を表示する
    private void SetText(final String msg, final int id){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(id == 0)
                    mTextViewInfo.setText(msg);
                else if(id == 1)
                    mTextViewData.setText(msg);
            }
        });
    }

    // ペアリング済のBTデバイス（"設定->Bluetooth"でペアリングする）を取得する ※現在電源OFFのデバイスも取得される
    private void GetBtDevices(){
        // スキャンしてデバイスを取得するのは時間がかかるが、こちらは一瞬で取得できる
        SetText("デバイスを検索中...", 0);
        ArrayList<BluetoothDevice> devices = mGPenBtLib.GetBtDevices();
        boolean result = false;
        if(devices != null && devices.size() > 0) {
            for (BluetoothDevice device : devices) {
                if(device.getName().equals(DEVICE_NAME) && device.getAddress().equals(DEVICE_ADDRESS)){
                    for(ParcelUuid uuid : device.getUuids()){
                        if(uuid.equals(DEVICE_UUID)){
                            // デバイスを確定
                            mBluetoothDevice = device;
                            mBluetoothDeviceUuid = uuid;
                            result = true;
                            SetText("BTデバイスを取得しました: " + "mBluetoothDevice.getName()=" + mBluetoothDevice.getName()
                                    + ", mBluetoothDevice.getAddress()=" + mBluetoothDevice.getAddress() + ", uuid=" + uuid.toString(), 0);
                        }
                        if(result)
                            break;
                    }
                }
                if(result)
                    break;
            }
        }
        if(!result){
            SetText("BTデバイスは検出されませんでした", 0);
        }
    }

    // BTペンを接続する
    public void ConnectBtDevice(){
        if(mBluetoothDevice == null || mBluetoothDeviceUuid == null){
            SetText("デバイスが選択されていません", 0);
            return;
        }
        mTextViewData.setVisibility(View.VISIBLE);
        int result = mGPenBtLib.ConnectBtDevice(mBluetoothDevice, mBluetoothDeviceUuid);
        if(result == 0)
            SetText("BTデバイス(" + mBluetoothDevice.getName() + "," + mBluetoothDevice.getAddress() + mBluetoothDeviceUuid.toString() +  ")を接続中...", 0);
        else
            SetText(String.format("接続できませんでした ErrorCode=%d", result), 0);
    }

    // BTペンの接続を解除する
    public void DisConnectBtDevice(){
        int result = mGPenBtLib.DisconnectBtDevice();
        if(result != 0)
            SetText(String.format("BTデバイスの接続を解除できませんでした ErrorCode=%d", result), 0);
    }

    // アクティブコードに対応付けた自作機能を実行する
    private void DispatchActiveCode(int dotcode){
        // ex)画像を表示する、動画を再生する、BTペンの接続を解除する など
        if(dotcode == 1){
            // ex)画像Aを表示する
        }else if(dotcode == 2){
            // ex)画像Bを表示する
        }
    }
}
