package org.qweyns.qweprotectstones.menus;

/** Сколько списали в цепочке действий меню — для возврата при сбое дальше по списку. */
public final class Payments {

    private double money;
    private int points;
    private int exp;

    public void addMoney(double amount) { money += amount; }

    public void addPoints(int amount) { points += amount; }

    public void addExp(int amount) { exp += amount; }

    public double money() { return money; }

    public int points() { return points; }

    public int exp() { return exp; }

    public boolean any() {
        return money > 0 || points > 0 || exp > 0;
    }

    /** Очки и уровни опыта не дробятся: цену округляем вверх, не в пользу игрока. */
    public static int ceilCost(double amount) {
        return (int) Math.ceil(amount);
    }
}
