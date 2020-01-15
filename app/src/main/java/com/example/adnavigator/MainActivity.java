package com.example.adnavigator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/**
 * Класс "показание"
 * Каждая запись содержит UUID и RSSI
 * */
class Reading{
    String UUID;
    Integer RSSI;
}

public class MainActivity<permissionStatus, val, bluetooth> extends AppCompatActivity {
    /// Опредение визуальных компонентов
    Button btnCheck;
    Button btnMail;
    Button btnClear;
    Button btnCallUID;
    Button btnVisionUID;
    TextView textUID;




    // Объявление блютузных классов

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    /// Объявление адаптеров для передачи данных на форму
    ArrayAdapter<String> mAdapter;
    ArrayAdapter<String> lAdapter;
    ArrayAdapter<String> rAdapter;

    /// Объявление массивов хранения данных
    ArrayList<String> ListScanRecords = new ArrayList<>(); //final???
    ArrayList<String> UniqueLabels = new ArrayList<>();
    ArrayList<Reading> ListReadingRecords = new ArrayList<>();
    ArrayList<String> readings = new ArrayList<>(); // Временный массив для кнопки
    private final static int REQUEST_ENABLE_BT = 1;

    /**
     * Функция получения списка уникальных меток
     * Входной параметр ArrayList<String> ScanMas -
     * Список всех показаний за время работы программы
     * Выходной параметр ArrayList<String> UniqLabels -
     * Список уникальных меток без повторений
     */
    ArrayList<String> CompareUIDWithScanRecords(ArrayList<String> ScanMas)
    {
        // Создаем пустой список для хранения UUID уникальных меток
        ArrayList<String> UniqLabels = new ArrayList<>();
        // Объявляем флаг для указания присутствия UUID в списке или нет
        boolean fl;
        for(int i=0;i<ScanMas.size();i++)
        {
            fl = false;
            for(int j=0;j<UniqLabels.size();j++)
                if(ScanMas.get(i).equals(UniqLabels.get(j)))
                {
                    fl=true;
                    continue;
                }
            if(fl == false)
                UniqLabels.add(ScanMas.get(i));
        }
        return UniqLabels;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /// Проверка и запрос разрешения на получение данных о местоположении устройства
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                &&
                ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
        }

        // найдем элементы
        ListView ListCheck = findViewById(R.id.ListCheck);
        final ListView ListUID = findViewById(R.id.ListUID);
        btnCheck = findViewById(R.id.btnCheck);
        btnMail = findViewById(R.id.btnMail);
        btnClear = findViewById(R.id.btnClear);
        btnVisionUID = findViewById(R.id.btnVisionUID);


        // создаем адаптеры
        mAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, ListScanRecords);
        lAdapter = new ArrayAdapter<String>(this,
               android.R.layout.simple_list_item_1, UniqueLabels);
        rAdapter = lAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, readings);

        // Привяжем массив через адаптер
        ListCheck.setAdapter(mAdapter);
        ListUID.setAdapter(lAdapter);
        ListUID.setAdapter(rAdapter);

        /**
         * Обновление данных об уникальных метках на форме
         * */
        View.OnClickListener oclVisionUID = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readings.clear();
                /// Получаем список уникальных меток
                UniqueLabels = CompareUIDWithScanRecords(ListScanRecords);
                int n=5; // количество измерений для усреднения
                // Функция будет работать если в списке ListScanRecords будет достаточно данных
                for (int i = 0; i<UniqueLabels.size(); i++)
                {
                    int t=0; // сколько раз встретили
                    double trssi=0; // сколько раз встретили
                    for (int j=ListReadingRecords.size()-1;j>0;j--){ // -1 для того, чтобы не выходил за пределы массива
                        if (t<n){
                            if (ListReadingRecords.get(j).UUID.equals(UniqueLabels.get(i))) {
                                t++;
                                trssi += ListReadingRecords.get(j).RSSI;
                            }
                        }
                        else
                        {
                            double SRrssi = trssi/t; // усреднейный RSSI
                            double Metr = (-0.0492*SRrssi*SRrssi)-6.767*SRrssi-227.81; // Перевод из rssi в метры
                            readings.add(UniqueLabels.get(i) + " " + Metr);
                            break;
                        }
                    }
                }
                Collections.sort(readings);
                rAdapter.notifyDataSetChanged();
            }
        };
        // создаем обработчик нажатия
        View.OnClickListener oclBtnOk = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Добавляем строку в ListView (btnCheck)
                mAdapter.notifyDataSetChanged();
                /// Запускаем сканер BLE меток

                BluetoothAdapter bluetooth= BluetoothAdapter.getDefaultAdapter();// при работе с bluetooth API нужно создать экземпляр класса BluetoothAdapter
                if (bluetooth.isEnabled()) {
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                    mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
                    mBluetoothLeScanner.startScan(mScanCallback);
                }
                else
                {
                    // Bluetooth выключен. Предложим пользователю включить его
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }


            }

        };
        /// Переопределим кнопку на показ данных UUID-RSSI-Расстояние
        View.OnClickListener oclBtnClear = new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                /** Пока не нужно
                /// Очистка списка считываний
                ListScanRecords.clear();
                mAdapter.notifyDataSetChanged();
                 */
            }
        };

        View.OnClickListener oclBtnMail = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /// Отправка результатов сканирования по почте
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/*");
                share.putExtra(Intent.EXTRA_SUBJECT, "Скан меток");
                share.putExtra(Intent.EXTRA_TEXT, ListScanRecords.toString());
                startActivity(Intent.createChooser(share, "SHARE RESULT"));
            }

        };

        // присвоим обработчик кнопке
        btnVisionUID.setOnClickListener(oclVisionUID);
        btnCheck.setOnClickListener(oclBtnOk);
        btnClear.setOnClickListener(oclBtnClear);
        btnMail.setOnClickListener(oclBtnMail);
    }
    // Не хватало но из другого источника
    public byte[] getIdAsByte(UUID uuid)
    {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
    /**
     * Переопределние функции получения результата от метки.
     * При получении пакета (событийно) обрабаываем данные как нам надо
     * */
    protected android.bluetooth.le.ScanCallback mScanCallback = new android.bluetooth.le.ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            /// Заносим считанные данные в переменную mScanRecord
            ScanRecord mScanRecord = result.getScanRecord();
            /// Переводим полученные данные у удобочитаемый вид
            String data= byteArrayToHexString(mScanRecord.getBytes());
            /// Получаем уровень сигнала RSSI
            int mRssi = -1;
            mRssi = result.getRssi();
            /// Отрезаем заголовок
            String dataType= data.substring(0,14);
            /// если заголовок совпадает со стандартным заголовком формата iBeacon (0201061aff4c00)
            if (dataType.equals("0201061aff4c00")){
                /// Заносим данные в массив показаний
                ListScanRecords.add(data.substring(18, 50));
                /// Занесение данных в список классов
                Reading temp=new Reading();
                temp.RSSI=mRssi;
                temp.UUID=data.substring(18, 50);
                ListReadingRecords.add(temp);
                /// Через адаптер обновляем данные на форме приложения
                mAdapter.notifyDataSetChanged();
            }
        }
    };
    /**
     * Функция перевода массива byte в строку
     * Данные с меток получаем в byte[].
     * Функция необходима, чтобы перевести его в удобочитаемый вид
     * */
    public static String byteArrayToHexString(final byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes){
            sb.append(String.format("%02x", b&0xff));
        }
        return sb.toString();
    }
}