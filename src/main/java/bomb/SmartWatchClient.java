package bomb;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.leshan.ResponseCode;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.DeregisterRequest;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.RegisterResponse;
import static org.eclipse.leshan.client.object.Security.noSecBootstap;
import static org.eclipse.leshan.LwM2mId.SERVER;

public class SmartWatchClient {

    private final LeshanClient client;

    // the registration ID assigned by the server
    private String registrationId;

    public SmartWatchClient(String serverHost, int serverPort) {

        ObjectsInitializer initializer = new ObjectsInitializer();
        //initializer.setClassForObject(3, Device.class);
        initializer.setInstancesForObject(0, noSecBootstap("coap://leshan.eclipse.org:5783"));
        initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
        initializer.setClassForObject(6, Location.class);
        //initializer.setClassForObject(3303, Temperature.class);
        List<LwM2mObjectEnabler> enablers = initializer.create(0, SERVER, 6);

        // Create client
        final InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);
        final InetSocketAddress localAddress = new InetSocketAddress("0.0.0.0", 0);
        
        client = new LeshanClient("foo", localAddress, localAddress, enablers);
        client.start();

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

    /*public static class Timer extends BaseInstanceEnabler {
    	@Override
        public ValueResponse read(int resourceid) {
            switch (resourceid) {
            case 5700: // Read value
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newDoubleValue(33)));
            default:
                return super.read(resourceid);
            }
    	}
    }
    
    public static class Temperature extends BaseInstanceEnabler {
    	@Override
        public ValueResponse read(int resourceid) {
            switch (resourceid) {
            case 5700: // Read value
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newDoubleValue(33)));
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
            }, 1000, 1000);*\/
        }

        @Override
        public ValueResponse read(int resourceid) {
            switch (resourceid) {
            case 0: // Manufacturer model
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(manufacturelModel)));

            case 1: // Model number
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(modelNumber)));

            case 2: // Serial number
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(serialNumber)));

            case 11: // Error code (mandatory resource)
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        new Value<?>[] { Value.newIntegerValue(0) }));

            case 13: // Current time
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newDateValue(new Date(currentTimestamp.get()))));

            case 16: // Binding mode (mandatory resource)
                return new ValueResponse(ResponseCode.CONTENT, new LwM2mResource(resourceid,
                        Value.newStringValue(bindingModel.toString())));

            default:
                return super.read(resourceid);
            }
        }

        @Override
        public LwM2mResponse write(int resourceid, LwM2mResource value) {
            switch (resourceid) {
            case 13: // current time
                currentTimestamp.set(((Date) value.getValue().value).getTime());
                System.out.println("New current date: " + new Date(currentTimestamp.longValue()));
                return new LwM2mResponse(ResponseCode.CHANGED);
            default:
                return super.write(resourceid, value);
            }
        }

        @Override
        public LwM2mResponse execute(int resourceid, byte[] params) {
            switch (resourceid) {
            case 4: // reboot resource
                System.out.println("Rebooting!");
                return new LwM2mResponse(ResponseCode.CHANGED);
            default:
                return super.execute(resourceid, params);
            }
        }

    }*/

    /*public void register() {

        RegisterResponse response = client.send(new RegisterRequest("my-smart-bomb"));

        if (response.getCode() == ResponseCode.CREATED) {
            registrationId = response.getRegistrationID();
            System.out.println("Registered with id: " + registrationId);
        }
    }*/

    public static void main(String[] args) {

        //String serverHost = "localhost";
        String serverHost = "leshan.eclipse.org";

        SmartWatchClient client = new SmartWatchClient(serverHost, 5683);
        //client.register();
    }
}