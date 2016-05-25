package bomb;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.object.Security;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.*;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.response.*;

import static org.eclipse.leshan.LwM2mId.*;

public class SmartBombClient {

    private final LeshanClient client;

    // the registration ID assigned by the server
    private String registrationId;
    private final String ENDPOINT_NAME = "SmartBomb";

    public SmartBombClient(String serverHost, int serverPort) {

        ObjectsInitializer initializer = new ObjectsInitializer();

        initializer.setInstancesForObject(SECURITY, Security.noSec("coap://"+serverHost+":"+Integer.toString(serverPort), 1));
        initializer.setInstancesForObject(SERVER, new Server(1, 300L, BindingMode.U, false));
        initializer.setInstancesForObject(DEVICE, new Device());
        initializer.setInstancesForObject(LOCATION, new Location());
        initializer.setInstancesForObject(3303, new Temperature());
        //initializer.setInstancesForObject(3340, new Timer());

        List<LwM2mObjectEnabler> enablers = initializer.create(
                SECURITY, SERVER, DEVICE, LOCATION, 3303/*,3340*/
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
        @Override
        public ReadResponse read(int resourceid) {
            switch (resourceid) {
                case 5700: // Read value
                    return ReadResponse.success(resourceid, 23.4);
                default:
                    return super.read(resourceid);
            }
        }
    }

    public static class Timer extends BaseInstanceEnabler {
        @Override
        public ReadResponse read(int resourceid) {
            switch (resourceid) {
                case 5700: // Read value
                    return ReadResponse.success(resourceid, 23.4);
                default:
                    return super.read(resourceid);
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

    public static void main(String[] args) {

        //String serverHost = "localhost";
        String serverHost = "leshan.eclipse.org";

        SmartBombClient client = new SmartBombClient(serverHost, 5683);
    }
}