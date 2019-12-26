package qxl.leetCode;

/**
 * @Author: qxl
 * @Date: 2019/8/23 16:31
 * @Desc:
 */
public class ListNode {
    int val;
    ListNode next;
    public ListNode(int x){val = x;}

    public int getVal() {
        return val;
    }

    public void setVal(int val) {
        this.val = val;
    }

    public ListNode getNext() {
        return next;
    }

    public ListNode setNext(ListNode next) {
        this.next = next;
        return next;
    }
}
