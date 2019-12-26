package qxl.sortAlgorithms;

import java.util.Arrays;

/**
 * @Author: qxl
 * @Date: 2019/8/19 14:27
 * @Desc: 插入排序：将数组数据逐一与已排序号的数据作比较，再将该数组元素插入适当的位置
 */
public class InsertionSort {
    public static void main(String[] args) {
        int[] arr = {1, 6, 3, 7, 2, 4, 5};
        Arrays.stream(insertion(arr)).forEach(i -> {
            System.out.println(i);
        });
    }

    public static int[] insertion(int[] nums){
        // 临时变量
        int temp;
        // 数组长度
        int size = nums.length;
        // 定位比较的元素
        int j;
        // 默认第一个元素已排好序，从第二个元素开始扫描，扫描 size - 1 次
        for (int i = 1; i < size; i++) {
            temp = nums[i];
            j = i - 1;
            // 将 nums[i] 和它的前一位比较，若小于
            /**
             *
             * 第一轮 while 开始时，i = 4，j = 3，数组是 [1,3,6,7,2,4,5]，temp = 2；
             * 第一轮 while 结束后，j = 2，数组是 [1,3,6,7,7,4,5]；
             * 第二轮 while 结束后，j = 1，数组是 [1,3,6,6,7,4,5]；
             * 第三轮 while 结束后，j = 0，数组是 [1,3,3,6,7,4,5]；
             * num[1] = temp 后，数组是 [1,2,3,6,7,4,5]；
             */
            while (j > 0 && temp < nums[j]){
                nums[j + 1] = nums[j];
                j--;
            }
            nums[j + 1] = temp;
        }
        return nums;
    }
}
