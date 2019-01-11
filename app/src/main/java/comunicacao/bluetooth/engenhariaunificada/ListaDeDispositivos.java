package comunicacao.bluetooth.engenhariaunificada;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;


public class ListaDeDispositivos extends AppCompatActivity {


    public static String ENDERECO = null;

    private ArrayAdapter<String> dispositivosPareados;

    private ArrayAdapter<String> dispositivosNovos;

    private BluetoothAdapter adaptadorBT;

    private ListView pareados;

    private ListView novos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_lista_de_dispositivos);

        adaptadorBT = BluetoothAdapter.getDefaultAdapter();

        buscaPorNovosDispositivos();

        dispositivosPareados = new ArrayAdapter<String>(this, R.layout.formato);
        dispositivosNovos = new ArrayAdapter<String>(this, R.layout.formato);

        pareados = (ListView) findViewById(R.id.pareados);
        pareados.setAdapter(dispositivosPareados);
        pareados.setOnItemClickListener(listener);

        novos = (ListView)findViewById(R.id.novos);
        novos.setAdapter(dispositivosNovos);
        novos.setOnItemClickListener(listener);

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        buscaPorDispositivosPareados();

    }

    public void buscaPorDispositivosPareados() {

        Set<BluetoothDevice> dispositivos = adaptadorBT.getBondedDevices();

        if (dispositivos.size() > 0) {

            for (BluetoothDevice device : dispositivos) {

                dispositivosPareados.add(device.getName() + "\n" + device.getAddress());

            }

        }else{
            dispositivosPareados.add("Nenhum dispositivo pareado");
        }

    }
    public void buscaPorNovosDispositivos() {

        if (adaptadorBT.isDiscovering()) {

            adaptadorBT.cancelDiscovery();
        }

        adaptadorBT.startDiscovery();

        Toast.makeText(this, "Buscando dispositivos...", Toast.LENGTH_LONG).show();

    }

    // Popula a lista de dispositivos a medida que ele vão sendo encontrados.
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {

                    dispositivosNovos.add(device.getName() + "\n" + device.getAddress());

                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {

                if (dispositivosNovos.getCount() == 0) {

                    dispositivosNovos.add("Nenhum dispositivo");
                }
            }
        }
    };

    // envia o endereço mac do dispositivo remoto que foi escolhido para a conexão.
    private AdapterView.OnItemClickListener listener = new AdapterView.OnItemClickListener(){

        @Override
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            adaptadorBT.cancelDiscovery();
            String item = ((TextView)v).getText().toString();
            String endereco = item.substring(item.length() - 17);

            Intent intent = new Intent();
            intent.putExtra(ENDERECO, endereco);

            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (adaptadorBT != null) {
            adaptadorBT.cancelDiscovery();
        }

        this.unregisterReceiver(mReceiver);
    }
}

