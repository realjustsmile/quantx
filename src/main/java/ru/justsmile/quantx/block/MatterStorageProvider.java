package ru.justsmile.quantx.block;

/**
 * Блок-хранилище материи: сам является баком ({@link MatterTank}) и
 * увеличивает ёмкость материализатора, если соединён с ним напрямую
 * или через сеть квантовых волокон/хабов ({@link MatterConduit}).
 */
public interface MatterStorageProvider extends MatterTank {

    /** Дополнительная ёмкость в микро-единицах материи. */
    long getMatterCapacityBonusMicro();
}
