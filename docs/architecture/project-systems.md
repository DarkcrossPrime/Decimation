# Project systems

The Last of Craft is not a weapon mod with extra features attached. Decimation is the internal codebase for a broader survival/world experience.

First-class systems are kept independent so progress on one does not force the project hierarchy around it:

```text
dev.decimation/
├── content/        data/catalog infrastructure shared by all content types
├── environment/    fog, atmosphere, weather-facing presentation, ambience
├── player/         first-person body/view, player presentation and movement-facing systems
├── world/          world interaction and survival/world rules
├── item/           generic non-weapon item behavior
├── block/          custom block behavior
├── vehicle/        vehicle definitions/runtime
├── weapon/         weapon definitions/runtime
├── network/        shared networking entry points
├── registry/       Minecraft registry integration
└── asset/          compiled Decimation runtime formats/loaders
```

Client-specific implementations will mirror those domains under `src/client/java/dev/decimation/` as they are introduced, for example `environment/client`, `player/client`, `vehicle/client` and `weapon/client`.

Weapons are the first subsystem being brought to a full vertical slice because they exercise input, networking, persistent item state, server authority, custom assets and rendering. They are not intended to become the architectural root of the mod.
