/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/9/10 17:12</create-date>
 *
 * <copyright file="Viterbi.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.algoritm;

import com.hankcs.hanlp.corpus.dictionary.item.EnumItem;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.dictionary.TransformMatrixDictionary;
import com.hankcs.hanlp.seg.common.Vertex;

import java.util.*;

/**
 * 维特比算法
 *
 * @author hankcs
 */
public class Viterbi
{
    /**
     * 求解HMM模型，所有概率请提前取对数
     *
     * @param obs     观测序列
     * @param states  隐状态
     * @param start_p 初始概率（隐状态）
     * @param trans_p 转移概率（隐状态）
     * @param emit_p  发射概率 （隐状态表现为显状态的概率）
     * @return 最可能的序列
     */
    public static int[] compute(int[] obs, int[] states, double[] start_p, double[][] trans_p, double[][] emit_p)
    {
        int _max_states_value = 0;
        for (int s : states)
        {
            _max_states_value = Math.max(_max_states_value, s);
        }
        ++_max_states_value;
        double[][] V = new double[obs.length][_max_states_value];
        int[][] path = new int[_max_states_value][obs.length];

        for (int y : states)
        {
            V[0][y] = start_p[y] + emit_p[y][obs[0]];
            path[y][0] = y;
        }

        for (int t = 1; t < obs.length; ++t)
        {
            int[][] newpath = new int[_max_states_value][obs.length];

            for (int y : states)
            {
                double prob = Double.MAX_VALUE;
                int state;
                for (int y0 : states)
                {
                    double nprob = V[t - 1][y0] + trans_p[y0][y] + emit_p[y][obs[t]];
                    if (nprob < prob)
                    {
                        prob = nprob;
                        state = y0;
                        // 记录最大概率
                        V[t][y] = prob;
                        // 记录路径
                        System.arraycopy(path[state], 0, newpath[y], 0, t);
                        newpath[y][t] = y;
                    }
                }
            }

            path = newpath;
        }

        double prob = Double.MAX_VALUE;
        int state = 0;
        for (int y : states)
        {
            if (V[obs.length - 1][y] < prob)
            {
                prob = V[obs.length - 1][y];
                state = y;
            }
        }

        return path[state];
    }

    /**
     * 特化版的求解HMM模型
     *
     * @param vertexList                包含Vertex.B节点的路径
     * @param transformMatrixDictionary
     */
    public static void compute(List<Vertex> vertexList, TransformMatrixDictionary<Nature> transformMatrixDictionary)
    {
        int length = vertexList.size() - 1;
        double[][] cost = new double[length][];
        Iterator<Vertex> iterator = vertexList.iterator();
        Vertex start = iterator.next();
        Nature pre = start.guessNature();
        // 第一个是确定的
        double total = 0.0;
        for (int i = 0; i < cost.length; ++i)
        {
            Vertex item = iterator.next();
            cost[i] = new double[item.attribute.nature.length];
            for (int j = 0; j < cost[i].length; ++j)
            {
                Nature cur = item.attribute.nature[j];
                cost[i][j] = total + transformMatrixDictionary.transititon_probability[pre.ordinal()][cur.ordinal()] - Math.log((item.attribute.frequency[j] + 1e-8) / transformMatrixDictionary.getTotalFrequency(cur));
            }
            double perfect_cost_line = Double.MAX_VALUE;
            int perfect_j = 0;
            for (int j = 0; j < cost[i].length; ++j)
            {
                if (perfect_cost_line > cost[i][j])
                {
                    perfect_cost_line = cost[i][j];
                    perfect_j = j;
                }
            }
            total = perfect_cost_line;
            pre = item.attribute.nature[perfect_j];
            item.confirmNature(pre);
        }
    }

    /**
     * 标准版的Viterbi算法，查准率高，效率稍低
     *
     * @param roleTagList               观测序列
     * @param transformMatrixDictionary 转移矩阵
     * @param <E>                       EnumItem的具体类型
     * @return 预测结果
     */
    public static <E extends Enum<E>> List<E> computeEnum(List<EnumItem<E>> roleTagList, TransformMatrixDictionary<E> transformMatrixDictionary)
    {
        int length = roleTagList.size() - 1;
        List<E> tagList = new LinkedList<E>();
        double[][] cost = new double[length][];
        Iterator<EnumItem<E>> iterator = roleTagList.iterator();
        EnumItem<E> start = iterator.next();
        E pre = start.labelMap.entrySet().iterator().next().getKey();
        // 第一个是确定的
        tagList.add(pre);
        double total = 0.0;
        // 第二个也可以简单地算出来
        Map.Entry<E, Integer>[] preEntryArray;
        {
            EnumItem<E> item = iterator.next();
            cost[0] = new double[item.labelMap.size()];
            Map.Entry<E, Integer>[] entryArray = new Map.Entry[item.labelMap.size()];
            Set<Map.Entry<E, Integer>> entrySet = item.labelMap.entrySet();
            Iterator<Map.Entry<E, Integer>> _i = entrySet.iterator();
            for (int _ = 0; _ < entryArray.length; ++_)
            {
                entryArray[_] = _i.next();
            }
            for (int j = 0; j < cost[0].length; ++j)
            {
                E cur = entryArray[j].getKey();
                cost[0][j] = total + transformMatrixDictionary.transititon_probability[pre.ordinal()][cur.ordinal()] - Math.log((item.getFrequency(cur) + 1e-8) / transformMatrixDictionary.getTotalFrequency(cur));
            }
            preEntryArray = entryArray;
        }
        // 第三个开始复杂一些
        for (int i = 1; i < cost.length; ++i)
        {
            EnumItem<E> item = iterator.next();
            cost[i] = new double[item.labelMap.size()];
            Map.Entry<E, Integer>[] entryArray = new Map.Entry[item.labelMap.size()];
            Set<Map.Entry<E, Integer>> entrySet = item.labelMap.entrySet();
            Iterator<Map.Entry<E, Integer>> _i = entrySet.iterator();
            for (int _ = 0; _ < entryArray.length; ++_)
            {
                entryArray[_] = _i.next();
            }
            int perfect_j = 0;
            double perfect_cost_line = Double.MAX_VALUE;
            for (int k = 0; k < cost[i].length; ++k)
            {
                cost[i][k] = Double.MAX_VALUE;
                for (int j = 0; j < cost[i - 1].length; ++j)
                {
                    E cur = entryArray[k].getKey();
                    double now = cost[i - 1][j] + transformMatrixDictionary.transititon_probability[preEntryArray[j].getKey().ordinal()][cur.ordinal()] - Math.log((item.getFrequency(cur) + 1e-8) / transformMatrixDictionary.getTotalFrequency(cur));
                    if (now < cost[i][k])
                    {
                        cost[i][k] = now;
                        if (now < perfect_cost_line)
                        {
                            perfect_cost_line = now;
                            perfect_j = j;
                        }
                    }
                }
            }
            pre = preEntryArray[perfect_j].getKey();
            tagList.add(pre);
            preEntryArray = entryArray;
        }
        return tagList;
    }

    /**
     * 仅仅利用了转移矩阵的“维特比”算法
     *
     * @param roleTagList               观测序列
     * @param transformMatrixDictionary 转移矩阵
     * @param <E>                       EnumItem的具体类型
     * @return 预测结果
     */
    public static <E extends Enum<E>> List<E> computeEnumSimply(List<EnumItem<E>> roleTagList, TransformMatrixDictionary<E> transformMatrixDictionary)
    {
        int length = roleTagList.size() - 1;
        List<E> tagList = new LinkedList<E>();
        double[][] cost = new double[length][];
        Iterator<EnumItem<E>> iterator = roleTagList.iterator();
        EnumItem<E> start = iterator.next();
        E pre = start.labelMap.entrySet().iterator().next().getKey();
        // 第一个是确定的
        tagList.add(pre);
        double total = 0.0;
        for (int i = 0; i < cost.length; ++i)
        {
            EnumItem<E> item = iterator.next();
            cost[i] = new double[item.labelMap.size()];
            Map.Entry<E, Integer>[] entryArray = new Map.Entry[item.labelMap.size()];
            Set<Map.Entry<E, Integer>> entrySet = item.labelMap.entrySet();
            Iterator<Map.Entry<E, Integer>> _i = entrySet.iterator();
            for (int _ = 0; _ < entryArray.length; ++_)
            {
                entryArray[_] = _i.next();
            }
            for (int j = 0; j < cost[i].length; ++j)
            {
                E cur = entryArray[j].getKey();
                cost[i][j] = total + transformMatrixDictionary.transititon_probability[pre.ordinal()][cur.ordinal()] - Math.log((item.getFrequency(cur) + 1e-8) / transformMatrixDictionary.getTotalFrequency(cur));
            }
            double perfect_cost_line = Double.MAX_VALUE;
            int perfect_j = 0;
            for (int j = 0; j < cost[i].length; ++j)
            {
                if (perfect_cost_line > cost[i][j])
                {
                    perfect_cost_line = cost[i][j];
                    perfect_j = j;
                }
            }
            total = perfect_cost_line;
            pre = entryArray[perfect_j].getKey();
            tagList.add(pre);
        }
//        if (HanLP.Config.DEBUG)
//        {
//            System.out.printf("viterbi_weight:%f\n", total);
//        }
        return tagList;
    }
}
