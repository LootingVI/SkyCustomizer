package de.flori.skycustomizer.config;

import de.flori.skycustomizer.features.ScoreboardCustomizer;

final class SidebarPreview {
    private SkyCustomizerConfig.ScoreboardConfig source;
    private ScoreboardCustomizer.SidebarSnapshot snapshot;
    private long next;
    ScoreboardCustomizer.SidebarSnapshot get(SkyCustomizerConfig.ScoreboardConfig style){
        long now=System.nanoTime();
        if(snapshot==null||source!=style||now>=next){source=style;snapshot=ScoreboardCustomizer.snapshot(style);next=now+(style.chromaTitle?33_000_000L:100_000_000L);}
        return snapshot;
    }
}
