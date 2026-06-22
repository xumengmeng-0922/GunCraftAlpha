package com.guncraft.launcher.core;

import java.util.ArrayList;
import java.util.List;

public class VersionManifest {
    /** 可选：远程清单 URL（JSON 格式与本文件相同），启动时合并更新 */
    public String remoteManifestUrl = "";
    /**
     * 可选：{@code owner/repo}。若未写 {@link #remoteManifestUrl}，则自动推导 Raw 清单地址：
     * {@code https://raw.githubusercontent.com/&lt;owner&gt;/&lt;repo&gt;/&lt;branch&gt;/docs/versions-manifest.json}
     */
    public String githubRepo = "";
    /** 与 {@link #githubRepo} 配合，默认 {@code main} */
    public String githubBranch = "";
    public List<GameVersion> versions = new ArrayList<>();
}
