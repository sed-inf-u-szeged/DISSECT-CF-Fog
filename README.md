<div align="center">
    <h1>DISSECT-CF-Fog Tutorial / Documentation GitHub Page</h1>
    <img src="assets/images/dcf-logo-min.png" alt="Simulator logo" width="100"/>
</div>

This branch contains the GitHub Page for an introductory tutorial for the [DISSECT-CF-Fog] Simulator.

#### The website's URL: https://sed-inf-u-szeged.github.io/DISSECT-CF-Fog

- this site is currently under development, feedback is appreciated;

- this site uses the [Just the Docs] theme;

## Setting up development environment
- First we have to download **Jekyll** (and Ruby), follow the instructions on the official Jekyll website according to your system:
  - [Windows]
  - [macOS]
  - [Ubuntu]
  - [FreeBSD]
  - [Other Linux]
- Clone the repo from the DISSECT-CF-Fog [gh-pages] branch.

With everything installed, to **build** and **preview** the created site locally:
- Open a terminal or command prompt in the root directory.
- Run `bundle install` command to install Ruby gems (libraries).
- Run `bundle exec jekyll serve` command to build the site (_site folder) and start the local server.
  - The default port is 4000, so the site can be accessed here: http://localhost:4000/DISSECT-CF-Fog/
  - Host and Port flags can be set for the command or defined in _config.yml

## Materials for development:
- [JustTheDocs documentation] - this should be more than enough
- [Markdown cheat sheet] - since we work in .md files it can be useful
- With JustTheDocs we handle many [Jekyll] and [Liquid] elements / syntax differently
  but understanding the original ones may help coordinate the JustTheDocs version.



[Just the Docs]: https://just-the-docs.github.io/just-the-docs/
[DISSECT-CF-Fog]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/

[Windows]: https://jekyllrb.com/docs/installation/windows/
[macOS]: https://jekyllrb.com/docs/installation/macos/
[Ubuntu]: https://jekyllrb.com/docs/installation/ubuntu/
[FreeBSD]: https://jekyllrb.com/docs/installation/freebsd/
[Other Linux]: https://jekyllrb.com/docs/installation/other-linux/

[gh-pages]: https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog/tree/gh-pages

[JustTheDocs documentation]: https://just-the-docs.com
[Markdown cheat sheet]: https://www.markdownguide.org/cheat-sheet/
[Jekyll]: https://jekyllrb.com/docs/step-by-step/01-setup/
[Liquid]: https://shopify.github.io/liquid/