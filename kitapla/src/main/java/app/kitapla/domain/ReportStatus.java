package app.kitapla.domain;

/**
 * OPEN      : yönetimin incelemesini bekliyor
 * ACTIONED  : incelendi ve işlem yapıldı
 * DISMISSED : incelendi, işlem gerekmedi
 */
public enum ReportStatus { OPEN, ACTIONED, DISMISSED }
