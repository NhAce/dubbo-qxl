package qxl.sortAlgorithms;

import java.util.Arrays;

/**
 * @Author: qxl
 * @Date: 2019/8/19 14:02
 * @Desc: 选择排序：如果需要对一个整数数组进行升序排序，先取数组的第一个元素的值，依次和第 2，3，... ，n 个位置的值进行比较
 *                 每次比较把较小的值放到第一个元素，等一轮比较完成后，第一个位置的值就是整个数组最小的值；接下来，第二个元素
 *                 依次和后面的值比较，找出剩下元素中最小的值放在第二个位置；依次类推，完成升序排序操作
 */
public class SelectionSort {
    public static void main(String[] args) {
        int[] arr = {4, 6, 3, 7, 2, 1, 5};
        Arrays.stream(selection(arr)).forEach(i -> {
            System.out.println(i);
        });
    }

    public static int[] selection(int[] nums){
        for (int i = 0; i < nums.length - 1; i++) {
            for (int i1 = i + 1; i1 < nums.length; i1++) {
                // > 升序
                // < 降序
                if (nums[i] > nums[i1]){
                    int temp = nums[i];
                    nums[i] = nums[i1];
                    nums[i1] = temp;
                }
            }
        }

        return nums;
    }
}
