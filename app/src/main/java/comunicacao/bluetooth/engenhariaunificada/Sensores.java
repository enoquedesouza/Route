package comunicacao.bluetooth.engenhariaunificada;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.math.BigDecimal;

import java.text.NumberFormat;
import java.text.DecimalFormat;

import java.math.RoundingMode;

/**
 * Classe responsável por monitorar os sensores de campo magnético e o acelerômetro do dispositivo mével.
 * A combinação dos dados de ambos os sensores permite encontrar a posição do dispositivo em relação ao polo norte
 * magnético. Esta informação será utilizada para manter o carrinho do kit na rota desejada.
 * Esta classe impelementa uma Thread para que o monitoramento dos sensores ocorra de maneira separa no processador e
 * implementa o SensorEventListener, uma infterface utilizada para ouvir eventos disparados pelos sensores.
 */

public class Sensores implements SensorEventListener {

    private SensorManager gerenciadorDoSensor; //Instância do SensorManager através da qual a aplicação consegue acessar os sensores.
    private final float[] leituraDoAcelerometro = new float[3]; //Vetor que armazenada as informações enviadas pelo acelerômetro.
    private final float[] leiturasDoMagnetometro = new float[3]; //Vetor que armazena as informações enviadas pelo sensor de campo magnético.
    private final float[] dadosDosSensores = new float[9]; //Este vetor combina os dados de ambos os sensores em um única estrutura de dados.
    // Este vetor será utilizado para calcular a posição do aparelho com relaçao ao polo  norte magnético.
    private final float[] angulosDeOrientacao = new float[3]; //Computa os angulos de orientacao que são calculados a partir dos dados dos sensores.
    private float referencia, orientacaoAtual;
    private BigDecimal valorArredondado;

    private Sensor magnetometro, acelerometro; //Declara as instâncias dos sensores  que serão utilizados.


    public Sensores(Context context){

        gerenciadorDoSensor = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        magnetometro = gerenciadorDoSensor.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        acelerometro = gerenciadorDoSensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        configuraSenbilidade();

    }

    //Método que define a sensibilidade desejada para os sensores.

    protected void configuraSenbilidade() {

        gerenciadorDoSensor.registerListener(this, acelerometro,SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        gerenciadorDoSensor.registerListener(this, magnetometro, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);

    }


    /**
     * Método da interface OnSensorListener que é chamado quando um evento de sensor é disparado. Como qualquer um
    * dos sensores podem disparar um evento, a implementação do método filtra apenas os eventos do magnetômetro e acelerômetro.
    * Os valores das leituras de ambos os sensores são recuperados e armazenados nos seus respectivos vetores
    **/

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor == acelerometro) {

            System.arraycopy(event.values, 0, leituraDoAcelerometro, 0, leituraDoAcelerometro.length);

            atualizaOsAngulosDeOrientacao();

        }

        else if (event.sensor == magnetometro) {

            System.arraycopy(event.values, 0, leiturasDoMagnetometro, 0, leiturasDoMagnetometro.length);

            atualizaOsAngulosDeOrientacao();
        }


    }


    //Método que recupera os valores de leitura dos sensores e converte em um vetor com os angulos de orientacao.

    public void atualizaOsAngulosDeOrientacao(){

        gerenciadorDoSensor.getRotationMatrix(dadosDosSensores, null, leituraDoAcelerometro, leiturasDoMagnetometro);
        gerenciadorDoSensor.getOrientation(dadosDosSensores, angulosDeOrientacao);

        configuraReferencia(angulosDeOrientacao[0]);
        configuraOrientacaoAtual(angulosDeOrientacao[0]);

    }


    //Configura um valor que será utilizado como referência para definir o deslocamento do dispositivo a partir desta referência.
    //Esta referencia é a direção inicial para onde o dispositivo está apontando a partir do pólo norte magnético.

    public void configuraReferencia(float referencia){

        if(this.referencia == 0.0){
            valorArredondado = new BigDecimal(referencia).setScale(1,BigDecimal.ROUND_HALF_EVEN);
            this.referencia = valorArredondado.floatValue();
        }
    }


    //Este método irá atualizar a orientacao do dispositivo para ser enviado ao kit toda vez que for solicitado.

    public void configuraOrientacaoAtual(float orientacaoAtual){

        valorArredondado = new BigDecimal(orientacaoAtual).setScale(1,BigDecimal.ROUND_HALF_EVEN);
        this.orientacaoAtual = valorArredondado.floatValue();;

    }


    public float getReferencia(){

        return referencia;

    }


    public float getOrientacaoAtual(){

        return orientacaoAtual;

    }

    @Override
    public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {

    }
}
