package org.faceletslite.imp;

import java.util.List;

public class LoopTagStatusImp {

    private int begin;
    private int end;
    private int step;
    private int index;
    private int count = 1;
    private final List<?> items;

    public LoopTagStatusImp(int begin, int end, int step, List<?> items) {
        this.begin = begin;
        this.end = end;
        this.step = step;
        this.index = begin;
        this.items = items;
    }

    public Object getCurrent() {
        return Safe.get(items, index);
    }

    public int getIndex() {
        return index;
    }

    public int getCount() {
        return count;
    }

    public boolean isFirst() {
        return index == begin;
    }

    public boolean isLast() {
        return index == end;
    }

    public boolean isEven() {
        return index % 2 == 0;
    }

    public boolean isOdd() {
        return index % 2 == 1;
    }

    public Integer getBegin() {
        return begin;
    }

    public Integer getEnd() {
        return end;
    }

    public Integer getStep() {
        return step;
    }

    public boolean hasNext() {
        return index <= end;
    }

    public void next() {
        index += step;
        count += 1;
    }
}
