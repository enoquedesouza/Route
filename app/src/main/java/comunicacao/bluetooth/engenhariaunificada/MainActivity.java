package comunicacao.bluetooth.engenhariaunificada;

import android.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;



public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {


    private static final int REQUISICAO_HABILITACAO_BT = 1; //Código para a requisitar a habilitação do bluetooth para o usuário.

    private static final int DISPOSITIVO_ESCOLHIDO = 2; //Quando um dispositivo bluetooth é escolhido este código é enviado.

    public static final int CHEGOU_DESTINO = 3; // Enviado ao veículo autônomo quando chegar ao destino.

    private BluetoothAdapter adaptadorBT; // Instância para o adaptador bluetooth do smartphone

    private BluetoothDevice dispositivoRemoto; //um instância para o dipositivo ao qual o smartphone irá se conectar.

    private BluetoothSocket socket; // Um socket para a comunicação entre o veículo e o smartphone.

    private ThreadBTConectado gerenteDaConexao; //Thread para gerenciar a conexão bluetooth.

    private DataInputStream entrada; //Stream para a entrada de dados.

    private DataOutputStream saida; // Stream para a saída de dados.

    private String leituraDoSocket; // Armazena as informações enviadas pelo dispositivo remoto.

    private MapFragment mapa; // Criam um fragmento de mapa para ser adicionado a tela inicial do aplicativo.

    private GoogleMap mMap; //Ao framgmento de mapa será adicionado um google map.

    private Button conectar, iniciaMovimento; // Botões para a interação com o usuário.

    private float referencia, orientacaoAtual; // Varáveis para armazenar as informações recuperadas do sensor.

    private double angulo; // Armazena o angulo de correção de direção a ser enviado para o veículo autônomo.

    private Sensores sensor; //Instância de Sensores oara acessar o sensor de rotação.

    //Códigos para resquisitar permissões ao usuário
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    //Define a taxa de atualização da posição do disposivo. A posição é recuperado do GPS do smartphone
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    //Parâmetros para acessar os serviços de localização do Google Maps
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;
    private boolean mRequestingLocationUpdates = false;

    //Localização atual e destino, este último é passado pelo usuário.
    private Location localizacaoAtual;
    private Location destino;
    private double latitude, longitude; // Armazenam as coordenadas de destinos passadas pelo usuário

    private final int INICIAR = 1; //Comando enviado pelo smartphone ao veículo autônomo para iniciar o movimento

    /*** Estabelece o serviço de conexão via Bluetooth entre o smartphone e o veículo autônomo ***/
    private static final UUID CONEXAO_INSEGURA = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

       //Inicializa o adaptador Blutooth do dispositivo.
        adaptadorBT = BluetoothAdapter.getDefaultAdapter();

        //Verifica se o adaptador foi inicializado corretamente
        if (adaptadorBT == null) {
            Toast.makeText(this, "Bluetooth não disponível", Toast.LENGTH_LONG).show();
            this.finish();
        }

        if (!adaptadorBT.isEnabled()) {

            //Exibe uma tela para o usuário autorizar a ativação do Bluetooth no smartphone.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUISICAO_HABILITACAO_BT);
        }

        //Cria um mapa e o adiciona na interface gráfica com a lolização atual do dispositivo.
        mapa = MapFragment.newInstance();
        android.app.FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.map, mapa);
        fragmentTransaction.commit();
        mapa.getMapAsync(this);


        /*** Incializa um botão na tela e adiciona um uma classe ouvinte de eventos de clique.
             Quando o usuário clicar no botão o método conecta() será chamado. ***/
        conectar = (Button) findViewById(R.id.conecta);
        conectar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                conecta();
            }
        });


        /*** Incializa um botão na tela e adiciona um uma classe ouvinte de eventos de clique.
             Quando o usuário clicar no botão o método conecta() será chamado. ***/
        iniciaMovimento = (Button) findViewById(R.id.iniciaMovimento);
        iniciaMovimento.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iniciaMovimento();
            }
        });

        /* Cria um cliente que consumirá  serviço de fornecimento de mapas do Google Maps. o Cliente é
        * uma instância desta classe que é um callback para o mapa.
        **/
        mRequestingLocationUpdates = false;
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);


        //Inicia os serviços de localização.
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();
        startLocationUpdates();

    }


    /*** O método conecta() exibe uma tela para o usuário com uma lista de dipositivos que estão
         pareados com smartphone. O usuário deverá escolher com qual dipositivo se conectar. ***/
    public void conecta(){

            Intent intent = new Intent(this, ListaDeDispositivos.class);
            startActivityForResult(intent, DISPOSITIVO_ESCOLHIDO);
    }

    public void iniciaMovimento(){

        /* O processo de iniciar o movimento consiste de uma sequencia de ações que devem recuperar as coordenadas de
           destino, configurá-lo, recuperar os dados de orientação e então enviar o comando para o veículo. */

        EditText lat = (EditText) findViewById(R.id.latitude);
        EditText lon = (EditText) findViewById(R.id.longitude);
        String lat1 = lat.getText().toString();
        String lon1 = lon.getText().toString();
        latitude = Double.parseDouble(lat1);
        longitude = Double.parseDouble(lon1);

        destino = new Location(localizacaoAtual.getProvider());
        destino.setLatitude(latitude);
        destino.setLongitude(longitude);

        sensor = new Sensores(this);
        referencia = sensor.getReferencia();
        orientacaoAtual = sensor.getOrientacaoAtual();
        lat.setText("");
        lon.setText("");

        //Um comando deve ser enviado ao kit para que o carrinho inicie o movimento.
        gerenteDaConexao.write(INICIAR);

    }

    /*
     *Este espera pelo resultado da interação do usuário com a lista de dispositivos diponíveis para a conexão.
     *Quando o usuário escolhe o dispositivo este método recupera o seu endereço MAC e passa para Thread responsável
     *pela conexão.
    */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == DISPOSITIVO_ESCOLHIDO && resultCode == RESULT_OK) {

            String enderecoMAC = data.getExtras().getString(ListaDeDispositivos.ENDERECO);
            dispositivoRemoto = adaptadorBT.getRemoteDevice(enderecoMAC);
            ThreadConectaBT conexaoCliente = new ThreadConectaBT(dispositivoRemoto);
            conexaoCliente.start();
            conectar.setText("CONECTADO");

        }
    }

    /*** A partir da referência e da orientação atual o método define qual melhor direção tomar
         por parte do veículo para a correção mais adequada, definindo de o veículo está a direita ou à
         Esquerda da sua referência inicial. ***/
    public void processaDadosDeOrientacao(){

        if(referencia > 0) {

            if(orientacaoAtual > 0) {

                if ((referencia - orientacaoAtual) < 0) {

                    //Então orientação atual está a direita e precisa ir para esquerda.

                    angulo = (Math.abs(referencia - orientacaoAtual) / 0.1) * 6;
                    angulo = -angulo;


                } else if ((referencia - orientacaoAtual) > 0) {

                    //Então a orientação está para a esquerda e o kit precisa ir para a direita.

                    angulo = (Math.abs(referencia - orientacaoAtual) / 0.1) * 6;
                }
            }

            if(orientacaoAtual < 0) {

                if ((referencia - orientacaoAtual) < 3) {

                    //Então será mais rápido corrigir a rota se o kit virar à direita.

                    angulo = (Math.abs(referencia - orientacaoAtual) / 0.1) * 6;

                } else if ((referencia - orientacaoAtual) > 3) {

                    //Então será mais rápido corrigir a rota se o kit virar à esquerda.

                    angulo = (Math.abs(referencia - orientacaoAtual) / 0.1) * 6;
                    angulo = 360 - angulo;
                    angulo = -angulo;


                }
            }
        }else if(referencia < 0){

            if(orientacaoAtual > 0) {

                if ((orientacaoAtual - referencia) < 3) {

                    //Então orientação atual está a direita e precisa ir para esquerda.

                    angulo = (Math.abs(orientacaoAtual - referencia) / 0.1) * 6;
                    angulo = -angulo;


                } else if ((orientacaoAtual - referencia) > 3) {

                    //Então a orientação está para a esquerda e o kit precisa ir para a direita.

                    angulo = (Math.abs(orientacaoAtual - referencia) / 0.1) * 6;
                    angulo = 360 - angulo;

                }
            }

            if(orientacaoAtual < 0) {

                if (orientacaoAtual < referencia) {

                    //Então orientação atual está a esquerda e precisa ir para direita.

                    angulo = (Math.abs(orientacaoAtual - referencia) / 0.1) * 6;

                } else if (orientacaoAtual > referencia) {

                    //Então a orientação está para a direita e o kit precisa ir para a esquerda.

                    angulo = (Math.abs(orientacaoAtual - referencia) / 0.1) * 6;
                    angulo = - angulo;


                }
            }

        }

    }

    /*
     * Esta classe interna é reponsável por realizar a conexão via Bluetooth entre o smartphone e um outro
     * dispositivo remoto. Neste caso, o dispositivo remoto será o veículo autônomo.
    */

    private class ThreadConectaBT extends Thread {

        private BluetoothDevice dispositivo;

        public ThreadConectaBT(BluetoothDevice dispositivo) {

            BluetoothSocket socketTMP = null;

            this.dispositivo = dispositivo;

            try {

                socketTMP = dispositivo.createInsecureRfcommSocketToServiceRecord(CONEXAO_INSEGURA);

            } catch (IOException e) { }

            socket = socketTMP;
        }

        public void run() {


            adaptadorBT.cancelDiscovery();

            try {

                socket.connect();

            } catch (IOException connectException) {

                try {

                    socket.close();

                } catch (IOException closeException) { }

                return;
            }

            gerenteDaConexao = new ThreadBTConectado(socket);
            gerenteDaConexao.start();

        }

        public void cancel() {

            try {

                socket.close();

            } catch (IOException e) { }
        }
    }


    /*
    * Esta thead é responsável por gerenciar a conexão com o veículo. A thread fica monitorando por solictações
    * de correção de direção por parte do veículo. Quando há necessidade de corrigir a direçao a thread ira´enviar a s
    * correções necessárias para o veículo.
    */

    private class ThreadBTConectado extends Thread {

        private final DataInputStream dataInputStream;
        private final DataOutputStream dataOutputStream;

        public ThreadBTConectado(BluetoothSocket socket) {


            try {

                entrada = new DataInputStream(socket.getInputStream());

                saida = new DataOutputStream(socket.getOutputStream());

            } catch (IOException e) { }

            dataInputStream = entrada;
            dataOutputStream = saida;

        }

        public void run() {

            byte[] buffer = new byte[1024]; //cria um buffer de 1Mb para armazenar os dados enviados pelo kit.
            int bytes;

            BufferedReader msg = new BufferedReader(new InputStreamReader(dataInputStream));

            // A Thread fica ouvindo o socket de entrada a espera dos dados enviados pelo kit.

            while (true) {

                try {

                    bytes = dataInputStream.read(buffer);
                    leituraDoSocket = new String(buffer);
                    enviaAngulo();

                } catch (IOException e) {

                    break;
                }
            }
        }

        //Chamado para enviar os dados para o dispositivo remoto.
        public void write(byte[] bytes) {

            try {

                dataOutputStream.write(bytes);
                dataOutputStream.flush();

            } catch (IOException e) {

            }
        }

        //Chamado para enviar os dados para o dispositivo remoto.
        public void write(int dado) {

            try {

                dataOutputStream.write(dado);
                dataOutputStream.flush();

            } catch (IOException e) {

            }
        }

        public void cancel() {

            try {

                entrada.close();

            } catch (IOException e) { }
        }
    }

    //envia o ângulo de correção na direção para o veículo
    public void enviaAngulo(){

        if(leituraDoSocket.contains("REQUISICAO")) {

            referencia = sensor.getReferencia();
            orientacaoAtual = sensor.getOrientacaoAtual();
            // a partir das iformações obtidas do sensor o angulo de correção na direção é calculo
            processaDadosDeOrientacao();
            gerenteDaConexao.write((int) angulo);

        }

    }


    private void createLocationRequest() {

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    }

    private void createLocationCallback() {

        mLocationCallback = new LocationCallback() {

            @Override

            public void onLocationResult(LocationResult locationResult) {

                super.onLocationResult(locationResult);

                localizacaoAtual = locationResult.getLastLocation();

                updateLocationUI();
            }
        };
    }

    private void buildLocationSettingsRequest() {

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();

        builder.addLocationRequest(mLocationRequest);

        mLocationSettingsRequest = builder.build();

    }

    private void startLocationUpdates() {

        // Begin by checking if the device has the necessary location settings.

        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)

                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {

                    @Override

                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {

                        //noinspection MissingPermission

                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,

                                mLocationCallback, Looper.myLooper());

                        updateUI();

                    }

                })

                .addOnFailureListener(this, new OnFailureListener() {

                    @Override

                    public void onFailure(@NonNull Exception e) {

                        int statusCode = ((ApiException) e).getStatusCode();

                        switch (statusCode) {

                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:

                                try {

                                    ResolvableApiException rae = (ResolvableApiException) e;

                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);

                                } catch (IntentSender.SendIntentException sie) {

                                }

                                break;

                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:

                                mRequestingLocationUpdates = false;

                        }
                        updateUI();
                    }

                });
    }

    private void updateUI() {

        updateLocationUI();

    }
    private void updateLocationUI() {

        if (localizacaoAtual != null) {

            if(destino!=null){

                if(localizacaoAtual.distanceTo(destino) < 10){

                    gerenteDaConexao.write(CHEGOU_DESTINO);

                }

            }

        }

    }

    private void showSnackbar(final int mainTextStringId, final int actionStringId, View.OnClickListener listener) {

        Snackbar.make(

                findViewById(android.R.id.content),
                getString(mainTextStringId),
                Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(actionStringId), listener).show();
    }

    private boolean checkPermissions() {

        int permissionState = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;

    }

    private void requestPermissions() {

        boolean shouldProvideRationale =

                ActivityCompat.shouldShowRequestPermissionRationale(this,

                        android.Manifest.permission.ACCESS_FINE_LOCATION);

        if (shouldProvideRationale) {

            showSnackbar(R.string.permission_rationale,

                    android.R.string.ok, new View.OnClickListener() {

                        @Override

                        public void onClick(View view) {

                            // Request permission

                            ActivityCompat.requestPermissions(MainActivity.this,

                                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},

                                    REQUEST_PERMISSIONS_REQUEST_CODE);

                        }

                    });

        } else {

            ActivityCompat.requestPermissions(MainActivity.this,

                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},

                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }

    }

    @Override

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {

            if (grantResults.length <= 0) {

            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                if (mRequestingLocationUpdates) {

                    startLocationUpdates();

                }

            } else {

                showSnackbar(R.string.permission_denied_explanation,

                        R.string.settings, new View.OnClickListener() {

                            @Override

                            public void onClick(View view) {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);

                            }

                        });
            }

        }

    }

    private void stopLocationUpdates() {

        mFusedLocationClient.removeLocationUpdates(mLocationCallback)

                .addOnCompleteListener(this, new OnCompleteListener<Void>() {

                    @Override

                    public void onComplete(@NonNull Task<Void> task) {

                        mRequestingLocationUpdates = false;

                    }

                });

    }


    // Ao abrir o aplicativo o mapa com a localização de são paulo e exibe. o mapa tem apenas efeito gráfico.
    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        LatLng sydney = new LatLng(-23.5489, -46.6388);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

    }

}
