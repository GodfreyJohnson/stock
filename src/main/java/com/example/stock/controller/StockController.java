package com.example.stock.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import javafx.util.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.util.StringUtils;

import static com.example.stock.utils.HttpUtils.doGet;
import static com.example.stock.utils.TimeUtils.getReportRatingTimeRangeStr;
import static com.example.stock.utils.TimeUtils.getTimeRange;

/**
 * 1.五线谱
 * 2.去年公司研报数量排行榜前10
 *
 * @author weibang
 * 2021/07/17
 */
@Controller
public class StockController {
    private static String caibaoshuoBaseUrl = "https://caibaoshuo.com/stock_charts/";
    private static String eastBaseUrl = "https://reportapi.eastmoney.com/";

    private static String sz = "SZSE:";
    private static String sh = "SHSE:";

    private ThreadPoolExecutor pool = new ThreadPoolExecutor(50, 100, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());

    @GetMapping("/")
    public String index(ModelMap map) {
        return "index";
    }

    @PostMapping("/query")
    @ResponseBody
    public JSONObject query(@RequestParam(name = "stock") String stock, @RequestParam String staffTime
            , @RequestParam String reportRatingTime) {
        JSONObject map = new JSONObject();
        // 五线谱数据
        Pair<Long, Long> staffPair = getTimeRange(staffTime);
        doGetStaffData(map, stock, staffPair.getKey(), staffPair.getValue());

        // 东方财富：研报排行榜
        Pair<String, String> reportRatingPair = getReportRatingTimeRangeStr(reportRatingTime, "yyyy-MM-dd");
        getEastReportRating(map, reportRatingPair.getKey(), reportRatingPair.getValue());
        return map;
    }

    private void getEastReportRating(JSONObject map, String from, String to) {
        ConcurrentHashMap<String, Integer> ratingMap = new ConcurrentHashMap<>();
        List<Integer> intList = IntStream.rangeClosed(1, 300).boxed().collect(Collectors.toList());
        CompletableFuture[] futures = intList.stream()
                .map(ele -> CompletableFuture.runAsync(() -> countEastReportRating(ratingMap, from, to, ele), pool))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        if (ratingMap.size() > 0) {
            JSONArray resArr = new JSONArray();
            for (Entry<String, Integer> entry : ratingMap.entrySet()) {
                JSONArray item = new JSONArray();
                item.add(entry.getKey());
                item.add(entry.getValue());
                resArr.add(item);
            }
            map.put("stockRating", resArr);
        }
    }

    private void countEastReportRating(ConcurrentHashMap<String, Integer> ratingMap, String from, String to, Integer i) {
        Long startGet = System.currentTimeMillis();
        String result = doGetEastReportList(from, to, i);
        Long endGet = System.currentTimeMillis();
        System.out.println("get page:" + i + " use time: " + (endGet - startGet) / 1000 + "s");
        if (result == null) {
            return;
        } else {
            JSONArray array = Optional.ofNullable(result)
                    .map(JSONObject::parseObject)
                    .map(json -> json.getJSONArray("data"))
                    .orElse(null);
            if (array == null || array.size() <= 0) {
                return;
            }
            array.forEach(item -> {
                String stockName = Optional.ofNullable(item.toString())
                        .map(JSONObject::parseObject)
                        .map(json -> json.getString("stockName"))
                        .orElse(null);
                if (!StringUtils.isEmpty(stockName)) {
                    if (ratingMap.containsKey(stockName)) {
                        Integer cnt = ratingMap.get(stockName);
                        ratingMap.put(stockName, cnt + 1);
                    } else {
                        ratingMap.put(stockName, 1);
                    }
                }
            });
        }
    }

    private String doGetEastReportList(String from, String to, int page) {
        String url = eastBaseUrl + "report/list?" +
//                "cb=datatable3564252&" +
                "industryCode=*&pageSize=100&industry=*&rating=&ratingChange=&" +
                "beginTime=" + from + "&endTime=" + to + "&pageNo=" + page + "&fields=&qType=0&" +
                "orgCode=&code=*&rcode=&sort=stockCode%2Casc&p=1&pageNo=1" +
                "&_=1627121472659";
        String result =  doGet(url, null);
        return result;
    }

    private void doGetStaffData(JSONObject map, String stock, Long start, Long end) {
        String staffStr = doGetStaffData(stock, start, end, sh);
        if (staffStr == null || staffStr.isEmpty() || staffStr.contains("no_data")) {
            staffStr = doGetStaffData(stock, start, end, sz);
        }
        if (staffStr == null || staffStr.isEmpty() || staffStr.contains("no_data")) {
            return;
        }
        JSONObject staffData = JSONObject.parseObject(staffStr);

        JSONArray timeArray = staffData.getJSONArray("t");
        if (timeArray == null || timeArray.size() == 0) {
            return;
        }
        List<String> timeList = new ArrayList<>();
        for (Object item : timeArray) {
            if (item != null) {
                String formatStr = "yyMMddHHmmss";
                SimpleDateFormat format = new SimpleDateFormat(formatStr);
                Date date = new Date(Long.parseLong(item.toString()) * 1000);
                timeList.add(format.format(date));
            }
        }
        map.put("time", timeList);
        map.put("tr", staffData.getJSONArray("tr"));
        map.put("price", staffData.getJSONArray("c"));
        map.put("tr_minus_1_std", staffData.getJSONArray("tr_minus_1_std"));
        map.put("tr_minus_2_std", staffData.getJSONArray("tr_minus_2_std"));
        map.put("tr_plus_1_std", staffData.getJSONArray("tr_plus_1_std"));
        map.put("tr_plus_2_std", staffData.getJSONArray("tr_plus_2_std"));
    }

    private String doGetStaffData(String stock, Long start, Long end, String prefix) {
        String postUrl = "history?symbol=" + prefix + stock + "&resolution=M&from=" + start + "&to=" + end
                + "&type=split_adjusted";
        String url = caibaoshuoBaseUrl + postUrl;
        return doGet(url, new HashMap<>());
    }

}
