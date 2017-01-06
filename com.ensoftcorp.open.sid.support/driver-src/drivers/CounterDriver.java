package drivers;

import tca.instrumentation.TCA_Counter;

public class CounterDriver {

	// change total work units to increase or decrease 
	// the number of collected data points
	private static final int TOTAL_WORK_TASKS = 100;
	
	public static void main(String[] args) throws Exception {
		for(int i=1; i<=TOTAL_WORK_TASKS; i++){
			TCA_Counter.getInstance().setSize(i);
			Object[] parameters = getWorkload(i);
			TCA_TARGET_METHOD_CALLSITE
		}
		tca.TCA.plotCounterRegression("Counter Workload Profile", TOTAL_WORK_TASKS);
	}
	
	private static Object[] getWorkload(int size){
		return null; // TODO: implement: return workload parameters for given size
	}
	
}