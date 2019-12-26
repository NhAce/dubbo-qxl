package qxl.sortAlgorithms;

import java.util.Arrays;

/**
 * @Author: qxl
 * @Date: 2019/8/19 13:33
 * @Desc: 冒泡排序：首先，通过外层的一个 for 循环，确定比较的轮数：数组长度 - 1
 *                 内层的 for 循环实现内部元素两两比较的结果，即把最大/最小的元素放最后（找出整个数组中最大/最小的元素），
 *                 每轮需要比较的次数比上一轮少一次
 */
public class BubbleSort {
    public static void main(String[] args) {
        int[] arr = {4, 6, 3, 7, 2, 1, 5};
        Arrays.stream(bubbleSort(arr)).forEach(i -> {
            System.out.println(i);
        });
    }

    public static int[] bubbleSort(int[] nums){
        for (int i = 0; i < nums.length - 1; i++) {
            boolean sortFlag = false;
            // 从第一个元素开始，相邻两个元素进行比较，大的/小的放在后面
            for (int i1 = 0; i1 < nums.length - 1 - i; i1++) {
                // > 表示升序
                // < 表示降序
                if (nums[i1] > nums[i1 + 1]){
                    // 升序
                    int temp = nums[i1];
                    nums[i1] = nums[i1 + 1];
                    nums[i1 + 1] = temp;
                    sortFlag = true;
                }
            }
            // 当前轮次没有发生排序动作，说明排序已完成，直接退出循环
            if (!sortFlag){
                System.out.println("进行 " + i + " 次排序后，数组元素已按顺序排列" );
                break;
            }
        }
        return nums;
    }
}
