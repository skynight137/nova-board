const androidModule = process.env.ANDROID_MODULE || "app";

if (!/^[A-Za-z0-9_.-]+$/.test(androidModule)) {
  throw new Error(
    "ANDROID_MODULE must be a simple Gradle module directory name",
  );
}

const versionFile = `${androidModule}/gradle.properties`;
const releaseDirectory = `${androidModule}/build/outputs/apk/release`;

module.exports = {
  branches: [
    "main",
    {
      name: "dev",
      prerelease: true,
    },
  ],
  plugins: [
    [
      "@semantic-release/commit-analyzer",
      {
        releaseRules: [{ type: "build", scope: "Needs bump", release: "patch" }],
      },
    ],
    "@semantic-release/release-notes-generator",
    [
      "@semantic-release/changelog",
      {
        changelogFile: "CHANGELOG.md",
      },
    ],
    [
      "@semantic-release/exec",
      {
        prepareCmd:
          "chmod +x .github/release-tooling/prepare-release.sh && .github/release-tooling/prepare-release.sh ${nextRelease.version}",
      },
    ],
    [
      "@semantic-release/git",
      {
        assets: ["CHANGELOG.md", versionFile],
        message:
          "chore: Release v${nextRelease.version} [skip ci]\\n\\n${nextRelease.notes}",
      },
    ],
    [
      "@semantic-release/github",
      {
        assets: [
          {
            path: `${releaseDirectory}/*.apk*`,
          },
        ],
        successComment: false,
      },
    ],
    [
      "@cleyrop-org/semantic-release-backmerge",
      {
        backmergeBranches: [
          {
            from: "main",
            to: "dev",
          },
        ],
        clearWorkspace: true,
      },
    ],
  ],
};