package tech.glasgowneuro.attysecg;

import java.util.Arrays;

class MatchedFilter {
    private float[] coefficients;
    private int size;
    private float[] buffer;
    private int index = 0;

    void set_coefficients(float[] Coefficients) {
        coefficients = Coefficients;
        size = coefficients.length;
        buffer = new float[size];

    }

    float matched_filter(float data) {
        buffer[index] = data;
        float[] array1 = reverse(Arrays.copyOfRange(buffer, 0, index));
        float[] array2 = reverse(Arrays.copyOfRange(buffer, index, size));

        int aLen = array1.length;
        int bLen = array2.length;
        float[] temporary = new float[aLen + bLen];

        System.arraycopy(array1, 0, temporary, 0, aLen);
        System.arraycopy(array2, 0, temporary, aLen, bLen);

        float output = sum(temporary, coefficients);

        if (index == size - 1) index = 0;
        else index++;

        return output;
    }

    // Array operators
    private float sum(float[] first, float[] second) {
        int length = Math.min(first.length, second.length);
        float result = 0;

        for (int i = 0; i < length; i++) {
            result += first[i] * second[i];
        }
        return result;
    }

    private float[] reverse(float[] input) {
        float[] output = new float[input.length];
        int count = 0;
        for (int i = input.length - 1; i >= 0; i--) {
            output[count] = input[i];
            count++;
        }
        return output;
    }
}
