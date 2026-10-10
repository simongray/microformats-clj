Benchmarks
==========

The benchmarks time the parser on large real pages, next to the time
that `html-pieces/parse` takes to read the same pages into Hiccup:

- **Parse** is `html-pieces/parse` of the HTML.
- **Microformats** is `mf/parse` of the Hiccup, i.e. what the parser adds
  when you already have the tree.
- **Share** is Microformats as a share of Parse.
- **Both** is `mf/parse` of the HTML.

Each time is the median of 21 rounds, after 10 rounds to warm up. They're
from an Apple M1 that was busy with other work, so take them as a rough
guide. The shares were the same in every run.

| Page                          | What                                                  |
| :---------------------------- | :---------------------------------------------------- |
| `indieweb-chat-2015-07-12`    | [IndieWeb chat log](https://chat.indieweb.org/2015-07-12), an h-entry with an h-card for each of 1,984 messages |
| `indieweb-chat-2017-06-24`    | [IndieWeb chat log](https://chat.indieweb.org/2017-06-24), the same markup |
| `wikipedia-falcon-9-launches` | [Wikipedia list](https://en.wikipedia.org/w/index.php?title=List_of_Falcon_9_and_Falcon_Heavy_launches&oldid=1379499403), thousands of links with rels |
| `ecma-262-2025`               | [ECMAScript 2025](https://tc39.es/ecma262/2025/), one page with no microformats |

The JVM, Java 24:

| Page                          |    Size |    Parse | Microformats | Share |     Both |
| :---------------------------- | ------: | -------: | -----------: | ----: | -------: |
| `indieweb-chat-2015-07-12`    | 1.83 MB |  65.7 ms |      32.8 ms |   50% | 110.4 ms |
| `indieweb-chat-2017-06-24`    | 1.48 MB |  54.1 ms |      26.7 ms |   49% |  87.7 ms |
| `wikipedia-falcon-9-launches` | 3.12 MB |  88.1 ms |      20.4 ms |   23% | 128.6 ms |
| `ecma-262-2025`               | 7.33 MB | 451.9 ms |      29.6 ms |    7% | 515.1 ms |

Node 24, compiled with advanced optimizations:

| Page                          |    Size |    Parse | Microformats | Share |     Both |
| :---------------------------- | ------: | -------: | -----------: | ----: | -------: |
| `indieweb-chat-2015-07-12`    | 1.83 MB | 117.8 ms |     102.9 ms |   87% | 230.5 ms |
| `indieweb-chat-2017-06-24`    | 1.48 MB |  88.2 ms |      82.9 ms |   94% | 176.3 ms |
| `wikipedia-falcon-9-launches` | 3.12 MB | 146.7 ms |      79.8 ms |   54% | 247.7 ms |
| `ecma-262-2025`               | 7.33 MB | 702.1 ms |     104.7 ms |   15% | 898.7 ms |

The chat logs are the hardest case, since nearly every element of them is
part of a microformat. Most pages have far fewer microformats than
elements, and there the parser adds little more than one walk of the
tree.

To run them, see the README.
