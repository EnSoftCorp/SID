package tca.instrumentation;

import java.util.Arrays;

public class TCA_Print {

	public static void print(Object value){
		if(value == null){
			System.out.println("TCA: null");
		} else {
			if(value instanceof int[]){
				int[] ints = (int[])value;
				System.out.println("TCA: int[" + ints.length + "] " + Arrays.toString(ints));
			} else if(value instanceof short[]){
				short[] shorts = (short[])value;
				System.out.println("TCA: short[" + shorts.length + "] " + Arrays.toString(shorts));
			} else if(value instanceof double[]){
				double[] doubles = (double[])value;
				System.out.println("TCA: double[" + doubles.length + "] " + Arrays.toString(doubles));
			} else if(value instanceof float[]){
				float[] floats = (float[])value;
				System.out.println("TCA: float[" + floats.length + "] " + Arrays.toString(floats));
			} else if(value instanceof byte[]){
				byte[] bytes = (byte[])value;
				System.out.println("TCA: byte[" + bytes.length + "] " + new String(bytes));
			} else if(value instanceof boolean[]){
				boolean[] booleans = (boolean[])value;
				System.out.println("TCA: boolean[" + booleans.length + "] " + Arrays.toString(booleans));
			} else {
				System.out.println("TCA: " + value.toString());
			}
		}
	}
	
}
