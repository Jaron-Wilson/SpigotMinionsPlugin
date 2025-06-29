package me.jaron.minion;

import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MinionStats implements ConfigurationSerializable {
    private long itemsMined;
    private long itemsPlaced;
    private long fortuneProcs;
    private long timesHarvested;
    private long bonemealApplied;
    private long doubleCropsProcs;
    private long creationTime;
    private long totalOperations; // total number of operations performed

    public MinionStats() {
        this.itemsMined = 0;
        this.itemsPlaced = 0;
        this.fortuneProcs = 0;
        this.timesHarvested = 0;
        this.bonemealApplied = 0;
        this.doubleCropsProcs = 0;
        this.creationTime = System.currentTimeMillis();
        this.totalOperations = 0;
    }

    public MinionStats(Map<String, Object> map) {
        this.itemsMined = ((Number) map.getOrDefault("itemsMined", 0)).longValue();
        this.itemsPlaced = ((Number) map.getOrDefault("itemsPlaced", 0)).longValue();
        this.fortuneProcs = ((Number) map.getOrDefault("fortuneProcs", 0)).longValue();
        this.timesHarvested = ((Number) map.getOrDefault("timesHarvested", 0)).longValue();
        this.bonemealApplied = ((Number) map.getOrDefault("bonemealApplied", 0)).longValue();
        this.doubleCropsProcs = ((Number) map.getOrDefault("doubleCropsProcs", 0)).longValue();
        this.creationTime = ((Number) map.getOrDefault("creationTime", System.currentTimeMillis())).longValue();
        this.totalOperations = ((Number) map.getOrDefault("totalOperations", 0)).longValue();
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("itemsMined", itemsMined);
        map.put("itemsPlaced", itemsPlaced);
        map.put("fortuneProcs", fortuneProcs);
        map.put("timesHarvested", timesHarvested);
        map.put("bonemealApplied", bonemealApplied);
        map.put("doubleCropsProcs", doubleCropsProcs);
        map.put("creationTime", creationTime);
        map.put("totalOperations", totalOperations);
        return map;
    }

    public void incrementItemsMined() {
        this.itemsMined++;
        this.totalOperations++;
    }

    public void incrementItemsPlaced() {
        this.itemsPlaced++;
        this.totalOperations++;
    }

    public void incrementFortuneProcs() {
        this.fortuneProcs++;
    }

    public void incrementTimesHarvested() {
        this.timesHarvested++;
        this.totalOperations++;
    }

    public void incrementBonemealApplied() {
        this.bonemealApplied++;
    }

    public void incrementDoubleCropsProcs() {
        this.doubleCropsProcs++;
    }

    // Getters
    public long getItemsMined() {
        return itemsMined;
    }

    public long getItemsPlaced() {
        return itemsPlaced;
    }

    public long getFortuneProcs() {
        return fortuneProcs;
    }

    public long getTimesHarvested() {
        return timesHarvested;
    }

    public long getBonemealApplied() {
        return bonemealApplied;
    }

    public long getDoubleCropsProcs() {
        return doubleCropsProcs;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getTotalOperations() {
        return totalOperations;
    }

    public double getFortunePercentage() {
        return itemsMined > 0 ? (double) fortuneProcs / itemsMined * 100 : 0;
    }

    public double getDoubleCropsPercentage() {
        return timesHarvested > 0 ? (double) doubleCropsProcs / timesHarvested * 100 : 0;
    }

    public double getBonemealPercentage() {
        return timesHarvested > 0 ? (double) bonemealApplied / timesHarvested * 100 : 0;
    }

    public long getTimeSaved(int delayInMs) {
        // Calculate time saved based on operations performed
        if (delayInMs <= 0) delayInMs = 5000; // Default 5 seconds if delay is invalid
        long totalTimeSavedMs = totalOperations * delayInMs;

        // Return in seconds
        return totalTimeSavedMs / 1000;
    }

    public String getFormattedTimeSaved(int delayInMs) {
        long seconds = getTimeSaved(delayInMs);
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString();
    }

    public String getFormattedUptime() {
        long currentTime = System.currentTimeMillis();
        long uptimeMillis = currentTime - creationTime;

        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString();
    }

    public void setItemsMined(long itemsMined) {
        this.itemsMined = itemsMined;
    }

    public void setItemsPlaced(long itemsPlaced) {
        this.itemsPlaced = itemsPlaced;
    }

    public void setFortuneProcs(long fortuneProcs) {
        this.fortuneProcs = fortuneProcs;
    }

    public void setTimesHarvested(long timesHarvested) {
        this.timesHarvested = timesHarvested;
    }

    public void setBonemealApplied(long bonemealApplied) {
        this.bonemealApplied = bonemealApplied;
    }

    public void setDoubleCropsProcs(long doubleCropsProcs) {
        this.doubleCropsProcs = doubleCropsProcs;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public void setTotalOperations(long totalOperations) {
        this.totalOperations = totalOperations;
    }
}
