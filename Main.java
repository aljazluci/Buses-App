import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Main {

    private static final String stationIdAttribute = "stop_id";
    private static final String stationNameAttribute = "stop_name";
    private static final String departureTimeAttribute = "departure_time";
    private static final String tripIdAttribute = "trip_id";
    private static final String routeIdAttribute = "route_id";

    public static void main(String[] args) {
        if (args.length <= 2){
            System.out.println("Insufficient arguments");
            return;
        }
        getBuses(args[0], Integer.parseInt(args[1]), args[2].equals("relative"));
    }

    public static void getBuses(String stationId, int numberOfBuses, boolean relative) {
        String curTime = new SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        String stationName = getStationName(stationId);
        Map<String, List<String>> arrivalMap = getArrivalMap(stationId, curTime);
        Map<String, List<String>> arrivalsByRoute = getArrivalsByRoute(arrivalMap);

        System.out.println("Name:\t" + stationName);
        for(String s: arrivalsByRoute.keySet()) {
            System.out.print(s + ":\t");
            int i = 0;
            for (String time: arrivalsByRoute.get(s)) {
                if (i >= numberOfBuses) break;
                if (!relative) {
                    System.out.print(time.substring(0, 5) + ", ");
                }else {
                    System.out.print(FileHandler.toMinutes(time) -
                            FileHandler.toMinutes(curTime) + "min, ");
                }
                i++;
            }
            System.out.println();
        }
    }

    public static Map<String, List<String>> getArrivalsByRoute(Map<String, List<String>> arrivalMap) {
        FileHandler fh = new FileHandler("./gtfs/trips.txt");
        Map<String, List<String>> arrivalsByRoute = null;
        try {
            arrivalsByRoute = fh.arrivalsByRoute(arrivalMap, tripIdAttribute, routeIdAttribute);
        } catch (IOException e) {
            System.out.println("Error getting arrivals by route");
        }
        fh.close();
        return arrivalsByRoute;
    }

    public static Map<String, List<String>> getArrivalMap(String stationId, String currentTime) {
        FileHandler fh = new FileHandler("./gtfs/stop_times.txt");
        Map<String, List<String>> b = null;
        try {
            List<String> routeLines = fh.getRouteLines(stationIdAttribute, stationId, departureTimeAttribute, currentTime);
            b = fh.getArrivals(departureTimeAttribute, routeLines, tripIdAttribute);
        } catch (IOException e) {
            System.out.println("Arrival map error");
        }
        fh.close();
        return b;
    }

    public static String getStationName(String stationId) {
        FileHandler fh = new FileHandler("./gtfs/stops.txt");
        String name = fh.getAttributeValueAt(stationId, stationIdAttribute, stationNameAttribute);
        fh.close();
        return name;
    }
}

class FileHandler {

    private Map<String, Integer> attributes;
    private BufferedReader br;
    public FileHandler(String fileName) {
        try {
            this.br = new BufferedReader(new FileReader(fileName));
            String line = this.br.readLine();
            Pattern pattern = Pattern.compile("[A-Za-z0-9_]+");
            while(!pattern.matcher(String.valueOf(line.charAt(0))).matches()) {
                line = line.substring(1);
            }
            this.attributes = new HashMap<>();
            int i = 0;
            for (String s: line.split(",")) {
                attributes.put(s, i++);
            }
        } catch (Exception e) {
            System.out.println("File not found");
        }

    }

    public String getAttributeValueAt (String value, String attributeOfValue, String attribute){
        String ln;
        try {
            if (!(this.attributes.containsKey(attributeOfValue) && this.attributes.containsKey(attribute))) return "noName";
            while ((ln = this.br.readLine()) != null) {
                String[] values = ln.split(",");
                if (values[attributes.get(attributeOfValue)].equals(value)) {
                    return values[attributes.get(attribute)];
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading file");
        }
        return "noName";
    }

    public List<String> getRouteLines(String stopIdAttribute, String stopIdValue,
                                      String departureTimeAttribute, String currentTimeValue)  throws IOException{
        // List of lines that are in time and contain the given station
        String ln;
        List<String> lines = new ArrayList<>();
        while((ln=this.br.readLine())!=null){
            String[] values = ln.split(",");
            if (toSeconds(currentTimeValue) > toSeconds(values[attributes.get(departureTimeAttribute)])) {
                continue;
            }
            if (tooMuchTime(currentTimeValue, values[attributes.get(departureTimeAttribute)], "02:00:00")) {
                continue;
            }
            if(values[attributes.get(stopIdAttribute)].equals(stopIdValue)){
                lines.add(ln);
            }
        }
        return lines;
    }

    public Map<String, List<String>> getArrivals (String departureTimeAttribute,
                                                  List<String> routeLines, String tripIdAttribute) {
        // map from route id to list of arrival times
        Map<String, List<String>> arrivals = new HashMap<>();
        for (String line: routeLines) {
            String[] values = line.split(",");
            String trip = values[attributes.get(tripIdAttribute)];
            if (!arrivals.containsKey(trip)) {
                arrivals.put(trip, new ArrayList<>());
                arrivals.get(trip).add(values[attributes.get(departureTimeAttribute)]);
            } else {
                arrivals.get(trip).add(values[attributes.get(departureTimeAttribute)]);
            }
        }
        return arrivals;
    }

    private static boolean tooMuchTime(String from, String to, String difference) {
        return toSeconds(to) - toSeconds(from) > toSeconds(difference);
    }

    private static int toSeconds(String time) {
        String[] times = time.split(":");
        return Integer.parseInt(times[0]) * 3600 + Integer.parseInt(times[1]) * 60 + Integer.parseInt(times[2]);
    }

    public static int toMinutes(String time) {
        String[] times = time.split(":");
        return Integer.parseInt(times[0]) * 60 + Integer.parseInt(times[1]);
    }

    public Map<String, List<String>> arrivalsByRoute(Map<String, List<String>> arrivals,
                                                     String tripIdAttribute, String routeIdAttribute) throws IOException{
        // joins departure times by route id and sorts the times
        Map<String, List<String>> arrivalsByRoute = new HashMap<>();
        String ln;
        while((ln = br.readLine()) != null) {
            String[] values = ln.split(",");
            if (arrivals.containsKey(values[attributes.get(tripIdAttribute)])) {
                if (!arrivalsByRoute.containsKey(values[attributes.get(routeIdAttribute)])) {
                    arrivalsByRoute.put(values[attributes.get(routeIdAttribute)], new ArrayList<>());
                    arrivalsByRoute.get(values[attributes.get(routeIdAttribute)])
                            .addAll(arrivals.get(values[attributes.get(tripIdAttribute)]));
                }
                else {
                    arrivalsByRoute.get(values[attributes.get(routeIdAttribute)])
                            .addAll(arrivals.get(values[attributes.get(tripIdAttribute)]));
                }
            }
        }
        for (String key: arrivalsByRoute.keySet()) {
            arrivalsByRoute.get(key).sort((a, b) -> toSeconds(a) - toSeconds(b));
        }
        return arrivalsByRoute;
    }

    public void close() {
        try {
            br.close();
        } catch (IOException e) {
            System.out.println("Error closing file");
        }
    }
}
