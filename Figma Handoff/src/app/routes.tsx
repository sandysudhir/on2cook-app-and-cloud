import { createHashRouter } from 'react-router';
import { Root } from './components/Root';
import { ProRoot } from './components/ProRoot';
import { HomeScreen } from './components/screens/HomeScreen';
import { CreateRecipeScreen } from './components/screens/CreateRecipeScreen';
import { LiveCookingScreen } from './components/screens/LiveCookingScreen';
import { RecipeLibraryScreen } from './components/screens/RecipeLibraryScreen';
import { DeviceScreen } from './components/screens/DeviceScreen';
import { DashboardScreen } from './components/screens/DashboardScreen';
import { SupportScreen } from './components/screens/SupportScreen';
import { ProfessionalEditorScreen } from './components/screens/ProfessionalEditorScreen';
import { LiveTimelineScreen } from './components/screens/LiveTimelineScreen';
import { RecipeExportScreen } from './components/screens/RecipeExportScreen';
import { RecipeCompletedScreen } from './components/screens/RecipeCompletedScreen';
import { PresetRecipeLibraryScreen } from './components/screens/PresetRecipeLibraryScreen';
import { PreEditorSetupScreen } from './components/screens/PreEditorSetupScreen';

export const router = createHashRouter([
  {
    path: '/',
    Component: Root,
    children: [
      { index: true, Component: HomeScreen },
      { path: 'create', Component: CreateRecipeScreen },
      { path: 'cooking', Component: LiveCookingScreen },
      { path: 'library', Component: RecipeLibraryScreen },
      { path: 'device', Component: DeviceScreen },
      { path: 'dashboard', Component: DashboardScreen },
      { path: 'support', Component: SupportScreen },
      // Export screen is portrait — lives under Root (no bottom nav shown)
      { path: 'export', Component: RecipeExportScreen },
      // Preset flow — portrait screens before entering landscape editor
      { path: 'preset-library', Component: PresetRecipeLibraryScreen },
      { path: 'preset-setup', Component: PreEditorSetupScreen },
    ],
  },
  {
    path: '/pro-editor',
    Component: ProRoot,
    children: [
      { index: true, Component: ProfessionalEditorScreen },
      { path: 'live', Component: LiveTimelineScreen },
      { path: 'completed', Component: RecipeCompletedScreen },
    ],
  },
]);
