# Design Extraction Log

Every design-tool (MCP) request is metered — a free Figma plan allows only a
handful per month. Log each run here so the remaining budget stays visible and
nothing gets re-extracted by accident.

**Plan / monthly budget:** Figma **Starter (free)** — hard cap of **~6 MCP tool
calls per month**. The local Figma desktop MCP server is *not* available on this
plan (it needs a Dev or Full seat on a paid plan), so the remote server at
`https://mcp.figma.com/mcp` is the only MCP option.
**Period resets:** monthly (Figma-side; exact reset date unconfirmed)

> **A "request" here is one MCP tool call, not one extraction run.** A single
> screen extraction can easily consume more than six calls, which is the entire
> monthly budget. Treat MCP as a last resort on this plan and prefer the
> zero-cost PNG-export path for anything a single frame can convey.

| Date | Screen / scope | Path | Requests spent | Written to |
|------|----------------|------|----------------|------------|
| 2026-08-17 | — (setup only) | Remote MCP server configured in `.mcp.json` | 0 | `extraction-log.md` |
| 2026-08-17 | Home / "Welcome Screen" (`1:2891`) | Remote MCP — `get_metadata` (find top-level frame), `get_variable_defs`, `get_design_context` | 3 | `tokens.md`, `screens/home.md` |

## Running total this period

**Spent:** 3 · **Remaining:** ~3

### Notes on the 2026-08-17 Home extraction

- `get_metadata` was spent locating the top-level frame because the supplied URL
  pointed at the page root (`node-id=0-1`) rather than a frame. A
  *Copy link to selection* URL would have saved this call.
- The frame yielded **only 5 colour variables** and no spacing, radius or
  typography variables — those were recovered from `get_design_context` as raw
  values and are recorded in `tokens.md` as "not yet variables".
- Asset binaries were **not** downloaded; MCP asset URLs expire after ~7 days.
  Re-exporting them later costs further calls unless done manually from Figma.
- **Do not re-extract this screen to resolve the open questions** — they are
  answerable by the user directly, or need design work in Figma first.

> PNG-export runs cost **0** requests — prefer them whenever a single frame is
> enough. Reserve MCP requests for exact token values across a whole design
> system or many component variants.
