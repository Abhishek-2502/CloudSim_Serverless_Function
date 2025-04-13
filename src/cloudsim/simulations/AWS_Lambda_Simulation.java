package cloudsim.simulations;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.*;

public class AWS_Lambda_Simulation {
    
    // Warm start duration in seconds
    private static final double WARM_START_DURATION = 5.0;
    
    public static void main(String[] args) {
        try {
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            // Initialize CloudSim
            CloudSim.init(numUsers, calendar, traceFlag);

            // Create Datacenter
            Datacenter datacenter = createDatacenter("Datacenter_0");

            // Create broker
            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            // Create VMs (initially create 2)
            List<Vm> vmList = new ArrayList<>();
            // VM parameters: id, userId, mips, numberOfPes, ram, bandwidth, storage, vmm, cloudletScheduler
            Vm vm1 = new Vm(0, brokerId, 1000, 2, 1024, 10000, 10000, "Xen", new CloudletSchedulerTimeShared());
            Vm vm2 = new Vm(1, brokerId, 1000, 2, 1024, 10000, 10000, "Xen", new CloudletSchedulerTimeShared());
            vmList.add(vm1);
            vmList.add(vm2);
            
            // Track the next VM ID to use when creating new VMs
            int nextVmId = 2;
            
            // Create execution schedule with hardcoded timing
            List<CloudletExecutionInfo> executionSchedule = createExecutionSchedule(brokerId);
            
            // Pre-process all cloudlets and assign VMs based on our Lambda policy
            // Track warm function instances and memory usage
            Map<Integer, Map<Integer, Double>> warmFunctions = new HashMap<>();
            Map<Integer, Boolean> warmStartStatus = new HashMap<>();
            Map<Integer, List<MemoryUsageRecord>> vmMemoryUsage = new HashMap<>();
            
            for (Vm vm : vmList) {
                vmMemoryUsage.put(vm.getId(), new ArrayList<>());
            }

            // Submit initial VM list
            broker.submitVmList(vmList);

            // Sort the execution schedule by time
            executionSchedule.sort(Comparator.comparingDouble(CloudletExecutionInfo::getExecutionTime));
            
            // Process the execution schedule for warm/cold starts
            double currentTime = 0.0;
            for (CloudletExecutionInfo info : executionSchedule) {
                Cloudlet cloudlet = info.getCloudlet();
                int functionType = info.getFunctionType();
                currentTime = info.getExecutionTime();  // Simulation time for this execution
                
                System.out.println("\nTime: " + currentTime + " - Processing function execution for cloudlet " + 
                                 cloudlet.getCloudletId() + " (Function Type: " + functionType + ")");
                
                // Check for warm instances of this function
                boolean foundWarmVM = false;
                int vmId = -1;
                
                if (warmFunctions.containsKey(functionType)) {
                    Map<Integer, Double> vmExecutionTimes = warmFunctions.get(functionType);
                    
                    // Look for a VM where this function is still warm
                    for (Map.Entry<Integer, Double> entry : new ArrayList<>(vmExecutionTimes.entrySet())) {
                        int candidateVmId = entry.getKey();
                        double lastExecutionTime = entry.getValue();
                        
                        if (currentTime - lastExecutionTime <= WARM_START_DURATION) {
                            // Found a warm VM for this function - check if memory is available
                            double currentMemoryUsage = calculateCurrentMemoryUsage(currentTime, vmMemoryUsage.get(candidateVmId));
                            double memoryNeeded = 0.4 * 1024; // 40% of VM RAM
                            
                            // Find VM in the list to get its RAM
                            Vm selectedVm = null;
                            for (Vm vm : vmList) {
                                if (vm.getId() == candidateVmId) {
                                    selectedVm = vm;
                                    break;
                                }
                            }
                            
                            if (selectedVm != null && (currentMemoryUsage + memoryNeeded) <= selectedVm.getRam()) {
                                // We have enough memory on this warm VM
                                System.out.println("Found warm VM " + candidateVmId + " for function " + functionType + 
                                                " (last execution: " + lastExecutionTime + ", elapsed: " + 
                                                (currentTime - lastExecutionTime) + "s)");
                                
                                vmId = candidateVmId;
                                foundWarmVM = true;
                                warmStartStatus.put(cloudlet.getCloudletId(), true);
                                break;
                            } else {
                                System.out.println("Found warm VM " + candidateVmId + " but insufficient memory");
                            }
                        }
                    }
                }
                
                // If no warm VM found or warm VM doesn't have enough memory, find VM with enough memory or create new VM
                if (!foundWarmVM) {
                    double memoryNeeded = 0.4 * 1024; // 40% of VM RAM
                    boolean vmFound = false;
                    
                    // Try to find any VM with enough memory
                    for (Vm vm : vmList) {
                        double currentMemoryUsage = calculateCurrentMemoryUsage(currentTime, vmMemoryUsage.get(vm.getId()));
                        
                        if ((currentMemoryUsage + memoryNeeded) <= vm.getRam()) {
                            vmId = vm.getId();
                            vmFound = true;
                            break;
                        }
                    }
                    
                    // If no VM with enough memory, create new VM
                    if (!vmFound) {
                        System.out.println("Creating new VM " + nextVmId + " due to memory constraints");
                        
                        // Create new VM
                        Vm newVm = new Vm(nextVmId, brokerId, 1000, 2, 1024, 10000, 10000, "Xen", new CloudletSchedulerTimeShared());
                        vmList.add(newVm);
                        
                        // Submit just this VM to broker
                        List<Vm> newVmList = new ArrayList<>();
                        newVmList.add(newVm);
                        broker.submitVmList(newVmList);
                        
                        // Initialize memory tracking for new VM
                        vmMemoryUsage.put(nextVmId, new ArrayList<>());
                        
                        // Assign cloudlet to new VM
                        vmId = nextVmId;
                        
                        // Increment VM counter for next time
                        nextVmId++;
                    }
                    
                    System.out.println("Cold start: Allocating function " + functionType + " to VM " + vmId);
                    warmStartStatus.put(cloudlet.getCloudletId(), false);
                }
                
                // Set the VM ID for cloudlet execution
                cloudlet.setVmId(vmId);
                
                // Update warm function tracking
                warmFunctions.putIfAbsent(functionType, new HashMap<>());
                warmFunctions.get(functionType).put(vmId, currentTime);
                
                // Set execution time for cloudlet
                double executionLength = warmStartStatus.get(cloudlet.getCloudletId()) ? 1.0 : 2.0;
                // Set cloudlet length to control execution time
                cloudlet.setCloudletLength((long)(executionLength * 500));  // Scale to match expected time
                
                // Record memory usage (40% RAM usage per cloudlet)
                double memoryUsage = 0.4 * 1024; // 40% of standard RAM
                
                // Add memory usage record
                vmMemoryUsage.get(vmId).add(new MemoryUsageRecord(currentTime, memoryUsage, true));
                vmMemoryUsage.get(vmId).add(new MemoryUsageRecord(currentTime + executionLength, memoryUsage, false));
                
                // Store additional information
                info.setVmId(vmId);
                info.setWarmStart(warmStartStatus.get(cloudlet.getCloudletId()));
                info.setFinishTime(currentTime + executionLength);
                
                // Check VM memory usage (just for display)
                double currentMemoryUsage = calculateCurrentMemoryUsage(currentTime, vmMemoryUsage.get(vmId));
                double totalMemory = 1024; // Standard VM RAM
                double memoryPercentage = (currentMemoryUsage / totalMemory) * 100;
                System.out.println("VM " + vmId + " memory usage: " + 
                                 String.format("%.2f", currentMemoryUsage) + "MB / " + 
                                 totalMemory + "MB (" + String.format("%.1f", memoryPercentage) + "%)");
                
                // Print VM warmup status
                printWarmStatus(currentTime, warmFunctions, vmList);
                
                // Print VM memory status
                printMemoryUsage(currentTime, vmMemoryUsage, vmList);
            }
            
            // Create a list for all the processed cloudlets
            List<Cloudlet> cloudletList = new ArrayList<>();
            for (CloudletExecutionInfo info : executionSchedule) {
                cloudletList.add(info.getCloudlet());
            }
            
            // Submit all cloudlets to broker
            broker.submitCloudletList(cloudletList);
            
            // Start the simulation
            System.out.println("\nStarting CloudSim simulation...");
            CloudSim.startSimulation();
            
            // Stop the simulation
            CloudSim.stopSimulation();

            // Print results
            List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
            System.out.println("Simulation completed.");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<CloudletExecutionInfo> createExecutionSchedule(int brokerId) {
        List<CloudletExecutionInfo> executionSchedule = new ArrayList<>();
        
        // Initial cloudlets (6 cloudlets at time 0)
        for (int i = 0; i <= 5; i++) {
            Cloudlet cloudlet = createCloudlet(i, brokerId);
            executionSchedule.add(new CloudletExecutionInfo(cloudlet, 0, i));
        }
        
        // Add reuse scenarios - Reuse function 2 after 3 seconds
        Cloudlet cloudlet2Again = createCloudlet(5, brokerId);
        executionSchedule.add(new CloudletExecutionInfo(cloudlet2Again, 3.0, 2)); 
        
        // Reuse function 4 after 6 seconds
        Cloudlet cloudlet4Again = createCloudlet(6, brokerId);
        executionSchedule.add(new CloudletExecutionInfo(cloudlet4Again, 6.0, 4)); 
        
        return executionSchedule;
    }

    private static void printWarmStatus(double currentTime, Map<Integer, Map<Integer, Double>> warmFunctions, List<Vm> vmList) {
        System.out.println("\n================ WARM FUNCTIONS STATUS ================");
        System.out.println("Current time: " + currentTime);
        System.out.println("+------+-------------------+-------------------------+");
        System.out.println("| VM   | Function Type     | Time Left Warm (sec)   |");
        System.out.println("+------+-------------------+-------------------------+");
        
        for (Vm vm : vmList) {
            boolean hasWarmFunctions = false;
            
            for (Map.Entry<Integer, Map<Integer, Double>> entry : warmFunctions.entrySet()) {
                int functionType = entry.getKey();
                Map<Integer, Double> vmTimes = entry.getValue();
                
                if (vmTimes.containsKey(vm.getId())) {
                    double lastExecTime = vmTimes.get(vm.getId());
                    double timeLeftWarm = WARM_START_DURATION - (currentTime - lastExecTime);
                    
                    if (timeLeftWarm > 0) {
                        System.out.format("| %-4d | %-17d | %-23.2f |\n", 
                            vm.getId(), functionType, timeLeftWarm);
                        hasWarmFunctions = true;
                    }
                }
            }
            
            if (!hasWarmFunctions) {
                System.out.format("| %-4d | %-17s | %-23s |\n", 
                    vm.getId(), "None", "N/A");
            }
        }
        
        System.out.println("+------+-------------------+-------------------------+");
    }
    
    private static void printMemoryUsage(double currentTime, Map<Integer, List<MemoryUsageRecord>> vmMemoryUsage, List<Vm> vmList) {
        System.out.println("\n================ VM MEMORY USAGE ================");
        System.out.println("Current time: " + currentTime);
        System.out.println("+------+-------------------+------------------+");
        System.out.println("| VM   | Used Memory (MB)  | Total Memory (MB)|");
        System.out.println("+------+-------------------+------------------+");
        
        for (Vm vm : vmList) {
            double currentMemoryUsage = calculateCurrentMemoryUsage(currentTime, vmMemoryUsage.get(vm.getId()));
            double totalMemory = vm.getRam();
            double memoryPercentage = (currentMemoryUsage / totalMemory) * 100;
            
            System.out.format("| %-4d | %-17.2f | %-16.2f | (%.1f%%)\n", 
                vm.getId(), currentMemoryUsage, totalMemory, memoryPercentage);
        }
        
        System.out.println("+------+-------------------+------------------+");
    }
    
    private static double calculateCurrentMemoryUsage(double currentTime, List<MemoryUsageRecord> memoryRecords) {
        if (memoryRecords == null) {
            return 0.0;
        }
        
        double totalUsage = 0;
        
        // Get active memory usage at the current time
        for (int i = 0; i < memoryRecords.size(); i++) {
            MemoryUsageRecord record = memoryRecords.get(i);
            
            if (record.getTime() <= currentTime) {
                if (record.isStart()) {
                    // Add memory if this is a start record
                    totalUsage += record.getMemoryUsage();
                } else {
                    // Subtract memory if this is an end record
                    totalUsage -= record.getMemoryUsage();
                }
            }
        }
        
        return Math.max(0, totalUsage); // Ensure we don't have negative memory
    }
    
    /**
     * Creates a cloudlet with standard parameters
     */
    private static Cloudlet createCloudlet(int id, int brokerId) {
        // Cloudlet parameters: id, length, pesNumber, fileSize, outputSize
        Cloudlet cloudlet = new Cloudlet(
            id, 
            1000, // length 
            1, 
            300, 
            300, 
            new UtilizationModelFull(), 
            new UtilizationModelRAM(), 
            new UtilizationModelFull()
        );
        
        cloudlet.setUserId(brokerId);
        return cloudlet;
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(10000)));  // 1 PE per host
        
        // Increase bandwidth and memory to avoid allocation failure
        hostList.add(new Host(0, new RamProvisionerSimple(8192), new BwProvisionerSimple(50000), 1000000, peList, new VmSchedulerTimeShared(peList)));
        
        try {
            return new Datacenter(name, new DatacenterCharacteristics("x86", "Linux", "Xen", hostList, 10, 3.0, 0.05, 0.1, 0.001), new VmAllocationPolicySimple(hostList), new LinkedList<>(), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }

    private static DatacenterBroker createBroker() {
        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("LambdaBroker");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return broker;
    }
    
    /**
     * Utility class to represent a CloudSim UtilizationModel that always returns 100% utilization
     */
    static class UtilizationModelFull implements UtilizationModel {
        @Override
        public double getUtilization(double time) {
            return 1.0; // 100% utilization
        }
    }
    
    /**
     * Custom RAM utilization model (40% utilization)
     */
    static class UtilizationModelRAM implements UtilizationModel {
        @Override
        public double getUtilization(double time) {
            return 0.4; // 40% RAM utilization
        }
    }
    
    /**
     * Class to track memory usage at specific times
     */
    static class MemoryUsageRecord {
        private double time;
        private double memoryUsage;
        private boolean isStart; // true if this is when memory starts being used, false if it's when memory is freed
        
        public MemoryUsageRecord(double time, double memoryUsage, boolean isStart) {
            this.time = time;
            this.memoryUsage = memoryUsage;
            this.isStart = isStart;
        }
        
        public double getTime() {
            return time;
        }
        
        public double getMemoryUsage() {
            return memoryUsage;
        }
        
        public boolean isStart() {
            return isStart;
        }
    }
    
    /**
     * Represents a scheduled cloudlet execution
     */
    static class CloudletExecutionInfo {
        private Cloudlet cloudlet;
        private double executionTime;
        private int functionType; // To track which function this cloudlet represents
        private int vmId;
        private boolean warmStart;
        private double finishTime;
        
        public CloudletExecutionInfo(Cloudlet cloudlet, double executionTime, int functionType) {
            this.cloudlet = cloudlet;
            this.executionTime = executionTime;
            this.functionType = functionType;
        }
        
        public Cloudlet getCloudlet() {
            return cloudlet;
        }
        
        public double getExecutionTime() {
            return executionTime;
        }
        
        public int getFunctionType() {
            return functionType;
        }
        
        public void setVmId(int vmId) {
            this.vmId = vmId;
        }
        
        public int getVmId() {
            return vmId;
        }
        
        public void setWarmStart(boolean warmStart) {
            this.warmStart = warmStart;
        }
        
        public boolean isWarmStart() {
            return warmStart;
        }
        
        public void setFinishTime(double finishTime) {
            this.finishTime = finishTime;
        }
        
        public double getFinishTime() {
            return finishTime;
        }
    }
}