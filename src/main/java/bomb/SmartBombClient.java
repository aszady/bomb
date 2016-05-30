package bomb;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.pi4j.io.gpio.*;
import com.pi4j.wiringpi.Gpio;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.object.Security;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.*;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.response.*;

import static org.eclipse.leshan.LwM2mId.*;
import static org.eclipse.leshan.core.model.ObjectLoader.loadJsonStream;

public class SmartBombClient {

    private final LeshanClient client;

    // the registration ID assigned by the server
    private String registrationId;
    private final String ENDPOINT_NAME = "SmartBomb";



    public SmartBombClient(String serverHost, int serverPort) throws InterruptedException, IOException {

        new Temperature().getTemperature();
        /*PinController pc = new PinController(RaspiPin.GPIO_02);

        for(;;) {
            pc.low();
            //System.out.println("--> GPIO state should be: OFF");
            Thread.sleep(1000);

            pc.high();
            //System.out.println("--> GPIO state should be: ON");
            Thread.sleep(100);
        }*/

        ObjectsInitializer initializer = new ObjectsInitializer(loadModel());

        initializer.setInstancesForObject(SECURITY, Security.noSec("coap://"+serverHost+":"+Integer.toString(serverPort), 1));
        initializer.setInstancesForObject(SERVER, new Server(1, 300L, BindingMode.U, false));
        initializer.setInstancesForObject(DEVICE, new Device());
        initializer.setInstancesForObject(LOCATION, new Location());
        initializer.setInstancesForObject(3303, new Temperature());
        initializer.setInstancesForObject(3311, new PinController(RaspiPin.GPIO_02));
        initializer.setInstancesForObject(3340, new Timer());

        List<LwM2mObjectEnabler> enablers = initializer.create(
                SECURITY, SERVER, DEVICE, LOCATION, 3303, 3311 ,3340
        );

        // Create client
        final InetSocketAddress localAddress = new InetSocketAddress(0);
        
        client = new LeshanClient(ENDPOINT_NAME, localAddress, localAddress, enablers);
        client.start();
        registrationId = client.getRegistrationId();
        System.out.printf("Registered as %s\n", registrationId);

        // De-register on shutdown and stop client
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (registrationId != null) {
                    client.stop(true);
                }
            }
        });

    }

    /**
     * Load the default LWM2M objects
     */
    public static LwM2mModel loadModel() {
        List<ObjectModel> models = ObjectLoader.loadDefault();
        URL resource = SmartBombClient.class.getResource("model");
        System.out.printf("URL = %s", resource);
        File f = null;
        try {
            f = Paths.get(resource.toURI()).toFile();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        models.addAll(ObjectLoader.load(f));
        return new LwM2mModel(models);
    }

    public static class PinController extends BaseInstanceEnabler {

        final GpioPinDigitalOutput output;

        public PinController(Pin pin) {
            final GpioController gpio = GpioFactory.getInstance();

            // provision gpio pin #01 as an output pin and turn on
            output = gpio.provisionDigitalOutputPin(pin, "MyLED", PinState.LOW);
            output.setShutdownOptions(true, PinState.LOW);
            Gpio.digitalWrite(2, false);
        }

        @Override
        public ReadResponse read(int resourceid) {
            switch (resourceid) {
                case 5850: // On/Off
                    return ReadResponse.success(resourceid, output.isHigh());
                default:
                    return super.read(resourceid);
            }
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            switch (resourceid) {
                case 5850: // On/Off
                    //output.setState((Boolean) value.getValue());
                    Gpio.digitalWrite(2, (Boolean) value.getValue());
                    fireResourcesChange(5850);
                    return WriteResponse.success();
                default:
                    return super.write(resourceid, value);
            }
        }

        public void high() {
            output.high();
        }

        public void low() {
            output.low();
        }
    }
    
    public static class Location extends BaseInstanceEnabler {
    	@Override
        public ReadResponse read(int resourceid) {
            switch (resourceid) {
            case 0: // Latitude
                return ReadResponse.success(resourceid, 33);
            case 1: // Longitude
            	return ReadResponse.success(resourceid, 44);
            default:
                return super.read(resourceid);
            }
    	}
    }

    public static class Temperature extends BaseInstanceEnabler {

        private double temp = 0;
        private boolean error = false;

        public Temperature() {
            new Thread(() -> {
                for(;;) {
                    try {
                        double oldTemp = temp;
                        temp = getTemperature();
                        if (temp != oldTemp)
                            fireResourcesChange(5700);
                        error = false;
                        Thread.sleep(1000);
                    } catch (IOException e) {
                        error = true;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        @Override
        public ReadResponse read(int resourceid) {
            switch (resourceid) {
                case 5700: // Read value
                    if (error)
                        return ReadResponse.internalServerError("Error reading temperature");
                    else
                        return ReadResponse.success(resourceid, temp);
                default:
                    return super.read(resourceid);
            }
        }

        public double getTemperature() throws IOException {
            Process proc = Runtime.getRuntime().exec(new String[]{"/opt/vc/bin/vcgencmd", "measure_temp"});
            InputStream is = proc.getInputStream();
            byte[] buf = new byte[100];
            int read = is.read(buf);
            is.close();
            String data = new String(buf);
            String[] items = data.split("[=']");
            double temperature = Double.parseDouble(items[1]);
            return temperature;
        }
    }

    public static class Timer extends BaseInstanceEnabler {

        private double remaining = 0;
        private boolean enabled = false;

        public Timer() {
            new Thread(() -> {
                for(;;) {
                    if (enabled && remaining > 0) {
                        remaining-=1;
                        fireResourcesChange(5538);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {}
                }
            }).start();
        }

        @Override
        public ReadResponse read(int resourceid) {
            switch (resourceid) {
                case 5538: // Remaining time
                    return ReadResponse.success(resourceid, remaining);
                case 5850: // Onoff
                    return ReadResponse.success(resourceid, enabled);
                default:
                    return super.read(resourceid);
            }
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            switch (resourceid) {
                case 5538: // Remaining time
                    remaining = (Double) value.getValue();
                    fireResourcesChange(resourceid);
                    return WriteResponse.success();
                case 5850: // Onoff
                    enabled = (Boolean) value.getValue();
                    fireResourcesChange(resourceid);
                    return WriteResponse.success();
                default:
                    return super.write(resourceid, value);
            }
        }

        @Override
        public ExecuteResponse execute(int resourceid, String params) {
            switch (resourceid) {
                case 5523: // Execute
                    System.out.println("executing!");
                    enabled = true;
                    return ExecuteResponse.success();
                default:
                    return super.execute(resourceid, params);
            }
        }


    }

   public static class Device extends BaseInstanceEnabler {

        private final String manufacturelModel = "The bomb manufacturer";
        private final String modelNumber = "2016";
        private final String serialNumber = "DASBHK787892500";
        private final BindingMode bindingModel = BindingMode.U;

        private AtomicLong currentTimestamp = new AtomicLong(0);

        public Device() {
            /*new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    currentTimestamp.getAndAdd(1000);
                    // fireResourceChange(13);
                }
            }, 1000, 1000);*/
        }

        @Override
        public ReadResponse read(int resourceid) {
            switch (resourceid) {
            case 0: // Manufacturer model
                return ReadResponse.success(resourceid, manufacturelModel);

            case 1: // Model number
                return ReadResponse.success(resourceid, modelNumber);

            case 2: // Serial number
                return ReadResponse.success(resourceid, serialNumber);

            case 11: // Error code (mandatory resource)
                return ReadResponse.success(resourceid, 0);

            case 13: // Current time
                return ReadResponse.success(resourceid, new Date(currentTimestamp.get()));

            case 16: // Binding mode (mandatory resource)
                return ReadResponse.success(resourceid, bindingModel.toString());

            default:
                return super.read(resourceid);
            }
        }

        @Override
        public WriteResponse write(int resourceid, LwM2mResource value) {
            switch (resourceid) {
            case 13: // current time
                currentTimestamp.set(((Date) value.getValue()).getTime());
                System.out.println("New current date: " + new Date(currentTimestamp.longValue()));
                return WriteResponse.success();
            default:
                return super.write(resourceid, value);
            }
        }

        @Override
        public ExecuteResponse execute(int resourceid, String params) {
            switch (resourceid) {
            case 4: // reboot resource
                System.out.println("Rebooting!");
                return ExecuteResponse.success();
            default:
                return super.execute(resourceid, params);
            }
        }

    }

    public static void main(String[] args) throws InterruptedException, IOException {

        //String serverHost = "localhost";
        String serverHost = "leshan.eclipse.org";

        SmartBombClient client = new SmartBombClient(serverHost, 5683);
    }
}