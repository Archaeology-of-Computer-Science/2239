package net.zzuhacker.android.nfc2369;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.apache.se.commoncodec.binary.Hex;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.tv_keya)
    TextView tv_keyA;
    @BindView(R.id.tv_cardUID)
    TextView tv_cardUID;
    @BindView(R.id.tv_dataRead)
    TextView tv_dataRead;
    @BindView(R.id.tv_keyb)
    TextView tv_keyB;
    @BindView(R.id.et_dataWrite)
    EditText et_dataWrite;
    @BindView(R.id.btn_read)
    Button btn_read;
    @BindView(R.id.btn_write)
    Button btn_write;
    NfcAdapter mAdapter=null;
    PendingIntent pendingIntent=null;
    IntentFilter[] mWriteTagFilters=null;
    String[][] mTechLists=null;

    private final  String blockData2369="97C8BF08013700C600004F04005300CC";
    private final byte[] blockData2369Bytes=new byte[]{(byte)0x97,(byte)0xC8,(byte)0xBF,0x08,0x01,0x37,0x00,(byte)0xC6,0x00,0x00,0x4F,0x04,0x00,0x53,0x00,(byte)0xCC};

    private NdefRecord[] records;
    private NdefMessage ndefMessage;

    private String TAG=getClass().getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initNFC();
    }
    private void initNFC() {
        // 获取nfc适配器，判断设备是否支持NFC功能
        mAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mAdapter == null) {
            Toast.makeText(this, "当前设备不支持NFC功能", Toast.LENGTH_SHORT).show();
        } else if (!mAdapter.isEnabled()) {
            Toast.makeText(this,"NFC功能未打开，请先开启后重试！",Toast.LENGTH_SHORT).show();
        }
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        ndef.addCategory("*/*");
        // 允许扫描的标签类型
        mWriteTagFilters = new IntentFilter[]{ndef};
        mTechLists = new String[][]{
                new String[]{MifareClassic.class.getName()},
               /* new String[]{NfcA.class.getName()}*/};// 允许扫描的标签类型
    }

    @Override
    protected void onResume() {
        super.onResume();
        //开启前台调度系统
        mAdapter.enableForegroundDispatch(this, pendingIntent, mWriteTagFilters, mTechLists);
    }

    @Override
    protected void onPause() {
        mAdapter.disableForegroundDispatch(this);
        super.onPause();
    }
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //当该Activity接收到NFC标签时，运行该方法
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) ||
                NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            byte[] bytesId = detectedTag.getId();
            String stringID=Hex.encodeHexString(bytesId);
            tv_cardUID.setText(stringID.toUpperCase());
            tv_keyB.setText(Hex.encodeHexString(getKeyB()).toUpperCase());
            tv_keyA.setText(Hex.encodeHexString(getKeyA(5,bytesId)).toUpperCase());
            byte[] balanceData=readBalanceBlock(detectedTag,getKeyA(5,bytesId),getKeyB());
            String balanceDataString=Hex.encodeHexString(balanceData).toUpperCase();
            tv_dataRead.setText(balanceDataString);

        }
    }
    private byte[] readBalanceBlock(Tag tag,byte[] keyA,byte[] keyB){
        MifareClassic mfc = MifareClassic.get(tag);
        byte[] data=null;
        try {
            mfc.connect();
            int blockIndex=mfc.sectorToBlock(5)+1;
            if(mfc.authenticateSectorWithKeyB(5,keyB)&&mfc.authenticateSectorWithKeyA(5,keyA)){
                data=mfc.readBlock(blockIndex);
                return data;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this,"扇区5区块0/2 连接/读取异常",Toast.LENGTH_SHORT);
        }finally {
            try{
                mfc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return data;
    }
    /**
     * 扇区读写
     * @param tag
     * @param sectorIndex  扇区索引  一般16个扇区 64块 ,0-15
     * @param blockIndex   0-3 每个扇区四个block
     * @return
     */
    public boolean writeTAG(Tag tag,int sectorIndex,int blockIndex,byte[] keyA,byte[] keyB,byte[] blockData) {
        MifareClassic mfc = MifareClassic.get(tag);
        try {
            mfc.connect();
            if (mfc.authenticateSectorWithKeyA(sectorIndex, keyA)&&mfc.authenticateSectorWithKeyB(sectorIndex,keyB)) {   //已知密码认证    r
                // the last block of the sector is used for KeyA and KeyB cannot be overwritted
                int block = mfc.sectorToBlock(sectorIndex)+blockIndex;
                mfc.writeBlock(block, blockData);
                mfc.close();
                Toast.makeText(this, "写入成功", Toast.LENGTH_SHORT).show();

                return true;
            }else if(mfc.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_NFC_FORUM)){     //新卡 未设密码认证  r
                int block = mfc.sectorToBlock(sectorIndex);
                mfc.writeBlock(block, "SGN-new000000000".getBytes());
                mfc.close();
                Toast.makeText(this,"新卡 写入成功",Toast.LENGTH_SHORT);
            } else{
                Toast.makeText(this,"未认证",Toast.LENGTH_SHORT);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this,"扇区连接异常",Toast.LENGTH_SHORT);

            try {
                mfc.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        return false;
    }
    /**
     * NFC m1
     *
     * @param ndef
     * @param tag
     * @param ndefMessage
     * @return
     * @throws IOException
     * @throws FormatException
     */
    private boolean writeMsg(Ndef ndef, Tag tag, NdefMessage ndefMessage) throws IOException, FormatException {
        try {
            if (ndef == null) {
                Toast.makeText(this,"格式化数据开始",Toast.LENGTH_SHORT);
                //Ndef格式类
                NdefFormatable format = NdefFormatable.get(tag);
                format.connect();
                format.format(ndefMessage);
            } else {
                Toast.makeText(this,"写入数据开始",Toast.LENGTH_SHORT);
                //数据的写入过程一定要有连接操作
                ndef.connect();
                ndef.writeNdefMessage(ndefMessage);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this,"IO异常，读写失败",Toast.LENGTH_SHORT);
        } catch (FormatException e) {
            e.printStackTrace();
            Toast.makeText(this,"格式化异常,读写失败",Toast.LENGTH_SHORT);
        } catch (NullPointerException e) {
            Toast.makeText(this,"格NullPointerException异常,读写失败",Toast.LENGTH_SHORT);
        }catch (IllegalStateException e){
            Toast.makeText(this,"Close other technology first!",Toast.LENGTH_SHORT);
        }
        return false;
    }
    private NdefRecord createTextRecord(String payload) {
        byte[] langBytes = Locale.getDefault().getLanguage().getBytes(Charset.forName("US-ASCII"));
        Charset utfEncoding = Charset.forName("UTF-8"); //encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = payload.getBytes(utfEncoding);
        int utfBit = 0;//encodeInUtf8 ? 0 : (1 << 7);
        char status = (char) (utfBit + langBytes.length);
        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte) status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
        NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
        return record;
    }
    /**
     * 读扇区
     * @return
     */
    private String readTag(Tag tag,MifareClassic mfc,int sectorIndex,int blockNo,byte[] keyA,byte[] keyB){
        for (String tech : tag.getTechList()) {
            System.out.println("------------"+tech);
        }
        //读取TAG
        try {
            String metaInfo = "";
            //Enable I/O operations to the tag from this TagTechnology object.
            mfc.connect();
            int type = mfc.getType();//获取TAG的类型
            int sectorCount = mfc.getSectorCount();//获取TAG中包含的扇区数
            String typeS = "";
            switch (type) {
                case MifareClassic.TYPE_CLASSIC:
                    typeS = "TYPE_CLASSIC";
                    break;
                case MifareClassic.TYPE_PLUS:
                    typeS = "TYPE_PLUS";
                    break;
                case MifareClassic.TYPE_PRO:
                    typeS = "TYPE_PRO";
                    break;
                case MifareClassic.TYPE_UNKNOWN:
                    typeS = "TYPE_UNKNOWN";
                    break;
            }
            metaInfo += "卡片类型：" + typeS + "\n共" + sectorCount + "个扇区\n共" 	+ mfc.getBlockCount() + "个块\n存储空间: " + mfc.getSize() + "B\n";
            int blockIndex;
            if (mfc.authenticateSectorWithKeyA(sectorIndex,keyA)&&mfc.authenticateSectorWithKeyB(sectorIndex,keyB) ) {
                blockIndex = mfc.sectorToBlock(sectorIndex)+blockNo;
                byte[] data = mfc.readBlock(blockIndex);
                metaInfo += "Block " + blockIndex + " : " + new String(data) + "\n";
            }else if( mfc.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_NFC_FORUM)){
                blockIndex = mfc.sectorToBlock(sectorIndex);
                byte[] data = mfc.readBlock(blockIndex);
                metaInfo += "新卡 Block " + blockIndex + " : " + new String(data) + "\n";

            }else {
                metaInfo += "Sector " + sectorIndex + ":验证失败\n";
            }
            return metaInfo;
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } finally {
            if (mfc != null) {
                try {
                    mfc.close();
                } catch (IOException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG)
                            .show();
                }
            }
        }
        return null;
    }

    /**
     * Convert byte[] to hex string
     *
     * @param src byte[] data
     * @return hex string
     */
    public static String bytesToHexString(byte[] src){
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }
    private byte[] getKeyA(int sector,byte[] cardId){
        byte[] a=new byte[6];
        if(cardId.length!=4){
            return null;
        }
        a[0]=(byte)sector;
        a[5]=(byte) 0x81;
        for(int i=0;i<4;i++){
            a[i+1]=(byte)~cardId[i];
        }
        return a;
    }
    private byte[] getKeyB()
    {
        byte[] b =new byte[]{(byte)0xA9,(byte)0xDE,0x7F,0x3C,(byte)0xEB,0x1F};
        return b;
    }
}
