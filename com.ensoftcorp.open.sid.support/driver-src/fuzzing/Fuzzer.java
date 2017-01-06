package fuzzing;

import java.util.Random;

public class Fuzzer {

	public static final String ALPHABET_LOWER = "abcdefghijklmnopqrstuvwxyz"; // [a..z]
	public static final String ALPHABET_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"; // [A..Z]
	public static final String DIGITS = "0123456789"; // [0..9]
	public static final String SPECIAL = "!@#$%^&*()-_=+";
	
	public static Integer[] generateRandomIntegerArray(int size) {
		Integer[] randArray = new Integer[size];
		Random rnd = new Random();
		for(int i=0; i<size; i++){
			randArray[i] = rnd.nextInt();
		}
		return randArray;
	}
	
	public static String generateRandomString(String alphabet, int length) {
		Random rnd = new Random();
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < length; i++) {
			buf.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
		}
		return buf.toString();
	}

}