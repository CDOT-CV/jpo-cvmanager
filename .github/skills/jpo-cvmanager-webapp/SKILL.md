---
name: jpo-cvmanager-webapp
description: "React/Redux Toolkit webapp development skill for jpo-cvmanager. Use when: creating new pages, components, Redux slices, RTK Query API slices, forms, tests, or any frontend feature in the webapp/ folder. Covers RTK Query caching with tags, Redux shared state vs local React state, Formik/Yup forms, Material UI, feature flags, routing, and strict TypeScript models."
argument-hint: "Describe the feature or component to build (e.g. 'add an RSU firmware page', 'create an RTK slice for notifications')"
---

# JPO CVManager Webapp — Development Skill

## Project Layout (webapp/src/)

```
src/
  EnvironmentVars.tsx          # Static env-var constants (CVIZ_API_SERVER_URL, feature flags …)
  feature-flags.tsx            # Route guards & conditional-render wrappers
  store.ts                     # Redux store — RootState lives here
  generalSlices/               # Slices shared across features (userSlice, intersectionSlice …)
  features/
    api/                       # RTK Query slices (rsuApiSlice, userApiSlice …)
    <featureName>/             # Feature folder: slice + component(s) co-located
  models/                      # *.d.ts type definitions ONLY — no logic
  pages/                       # Top-level routed page components
  components/                  # Shared re-usable components
    Tabs.tsx / VerticalTabs.tsx
  styles/
    index.ts                   # Custom MUI theme tokens
    components/                # Styled sub-components (AdminButton, Messages …)
  public/icons/                # Custom SVG/PNG icons
```

---

## 1. Types & Models

All shared types live in `src/models/` as `.d.ts` files. Component-local types (props interfaces, form shapes) go at the top of the `.tsx` file, just below imports.

```ts
// src/models/Widget.d.ts
export interface Widget {
  id: string
  name: string
  organizationId: string
}
export interface WidgetCreationBody {
  name: string
  organizationId: string
}
```

---

## 2. RTK Query Slice

Reference: [src/features/api/rsuApiSlice.ts](../../webapp/src/features/api/rsuApiSlice.ts)

### Rules
- File location: `src/features/api/<name>ApiSlice.ts`
- Slice-specific interface types go **below** imports, above the slice
- Tag name constants: `UPPER_SNAKE_CASE as const`
- `baseUrl` from `EnvironmentVars.CVIZ_API_SERVER_URL`
- `prepareHeaders`: always pull `selectToken` from state and set `Authorization: Bearer ${token}`
- Use `getQueryString` from `intersectionApiSlice` for query-string serialisation
- Export `useGet*Query`, `useLazy*Query`, `use*Mutation` hooks

### Minimal sample

```ts
// src/features/api/widgetApiSlice.ts
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import EnvironmentVars from '../../EnvironmentVars'
import { RootState } from '../../store'
import { selectToken } from '../../generalSlices/userSlice'
import { getQueryString } from './intersectionApiSlice'
import { Widget, WidgetCreationBody } from '../../models/Widget'

// ── Slice-local types ──────────────────────────────────────────────────────────
export interface GetWidgetsParams {
  organization: string
}

// ── Tag constants ──────────────────────────────────────────────────────────────
export const WIDGET_TAG           = 'Widget'           as const
export const WIDGET_LIST_ID       = 'LIST'             as const

// ── Slice ──────────────────────────────────────────────────────────────────────
export const widgetApiSlice = createApi({
  reducerPath: 'widgetApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${EnvironmentVars.CVIZ_API_SERVER_URL}/widgets`,
    prepareHeaders: (headers, { getState }) => {
      const token = selectToken(getState() as RootState)
      headers.set('Accept', 'application/json')
      if (token) headers.set('Authorization', `Bearer ${token}`)
      return headers
    },
  }),
  tagTypes: [WIDGET_TAG],
  endpoints: (builder) => ({
    getWidgets: builder.query<Widget[], GetWidgetsParams>({
      query: ({ organization }) => ({
        url: '',
        headers: { Organization: organization },
      }),
      providesTags: (result) =>
        result
          ? [...result.map(({ id }) => ({ type: WIDGET_TAG, id })),
             { type: WIDGET_TAG, id: WIDGET_LIST_ID }]
          : [{ type: WIDGET_TAG, id: WIDGET_LIST_ID }],
    }),
    getWidget: builder.query<Widget, string>({
      query: (id) => getQueryString({ widget_id: id }),
      providesTags: (_, __, id) => [{ type: WIDGET_TAG, id }],
    }),
    createWidget: builder.mutation<void, WidgetCreationBody>({
      query: (body) => ({ url: '', method: 'POST', body }),
      invalidatesTags: [{ type: WIDGET_TAG, id: WIDGET_LIST_ID }],
    }),
    patchWidget: builder.mutation<void, { id: string; patch: Partial<Widget> }>({
      query: ({ id, patch }) => ({
        url: getQueryString({ widget_id: id }),
        method: 'PATCH',
        body: patch,
      }),
      invalidatesTags: (_, __, { id }) => [
        { type: WIDGET_TAG, id },
        { type: WIDGET_TAG, id: WIDGET_LIST_ID },
      ],
    }),
    deleteWidget: builder.mutation<void, string>({
      query: (id) => ({ url: getQueryString({ widget_id: id }), method: 'DELETE' }),
      invalidatesTags: (_, __, id) => [
        { type: WIDGET_TAG, id },
        { type: WIDGET_TAG, id: WIDGET_LIST_ID },
      ],
    }),
  }),
})

export const {
  useGetWidgetsQuery,
  useLazyGetWidgetsQuery,
  useGetWidgetQuery,
  useLazyGetWidgetQuery,
  useCreateWidgetMutation,
  usePatchWidgetMutation,
  useDeleteWidgetMutation,
} = widgetApiSlice
```

### Caching & invalidation checklist
- List queries: `providesTags` includes both per-item tags and `{ type, id: 'LIST' }`
- Create/delete mutations: `invalidatesTags` the `LIST` id
- Patch mutations: `invalidatesTags` both the item id **and** the `LIST` id
- Use `skip` option when the required parameter is not yet available: `useGetWidgetQuery(id, { skip: !id })`

---

## 3. Redux Slice

Reference: [src/generalSlices/userSlice.ts](../../webapp/src/generalSlices/userSlice.ts)

### Rules
- Page/feature slices → `src/features/<feature>/<feature>Slice.ts`
- Shared slices → `src/generalSlices/<name>Slice.ts`
- Define `initialState` as a typed `const` first; re-use it in the slice's `initialState.value`
- `loading` boolean always included at the top level of slice state
- Async thunks: named `<sliceName>/<methodName>`, check token with `condition` guard when needed
- One selector per state field; specialized selectors for filter/combine operations
- All selectors typed with `(state: RootState)`

### Minimal sample

```ts
// src/features/widget/widgetSlice.ts
import { PayloadAction, createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { RootState } from '../../store'
import { Widget } from '../../models/Widget'

// ── Initial state ──────────────────────────────────────────────────────────────
const initialState = {
  selectedWidgetId: null as string | null,
  filter: '',
}

// ── Async thunks ───────────────────────────────────────────────────────────────
// Use async thunks to orchestrate multi-step data operations, not simple CRUD.
// Simple CRUD should use RTK Query mutations directly in the component.

// ── Slice ──────────────────────────────────────────────────────────────────────
export const widgetSlice = createSlice({
  name: 'widget',
  initialState: {
    loading: false,
    value: initialState,
  },
  reducers: {
    setSelectedWidgetId: (state, action: PayloadAction<string | null>) => {
      state.value.selectedWidgetId = action.payload
    },
    setFilter: (state, action: PayloadAction<string>) => {
      state.value.filter = action.payload
    },
  },
})

export const { setSelectedWidgetId, setFilter } = widgetSlice.actions

// ── Selectors ──────────────────────────────────────────────────────────────────
export const selectSelectedWidgetId = (state: RootState) => state.widget.value.selectedWidgetId
export const selectFilter           = (state: RootState) => state.widget.value.filter
export const selectWidgetLoading    = (state: RootState) => state.widget.loading

export default widgetSlice.reducer
```

---

## 4. React Components

### State ownership
| State type | Where to store |
|---|---|
| Toggle open/closed, local form step, hover | `useState` |
| Data shared between sibling components | Redux slice |
| Complex interdependent calculations | Redux slice (with `createSelector`) |
| Server data | RTK Query (never duplicate in slice) |

### Dispatch pattern (always use this exact signature)
```ts
const dispatch: ThunkDispatch<RootState, void, AnyAction> = useDispatch()
```

### Selectors
```ts
const selectedId = useSelector(selectSelectedWidgetId)
const organization = useSelector(selectOrganizationName)
```

### Props
Define a local interface or type immediately after imports, before the component:

```tsx
interface WidgetCardProps {
  widgetId: string
  onDelete: (id: string) => void
}

const WidgetCard = ({ widgetId, onDelete }: WidgetCardProps) => { … }
```

### Routing
- Every page accessible via a unique route in the router config
- Entity detail/edit pages include the entity ID: `/dashboard/admin/widgets/:widgetId`
- All route groups end with a `*` catchall to `<NotFound>` (`src/pages/404.tsx`)
- Navigation: `useNavigate()`; URL parsing: `useLocation()` / `useParams()`
- Tabs: use `src/components/Tabs.tsx`; vertical tabs: `src/components/VerticalTabs.tsx`

### Material UI
- Use MUI for all components and icons (no raw HTML form elements except inside `<Form>` wrappers)
- Custom theme tokens from `src/styles/index.ts` (`headerTabHeight`, palette extensions …)
- Custom SVG/PNG icons → `public/icons/`

### Feature flags
Wrap feature-specific routes and renders:
```tsx
// Route-level guard (redirects to "/" if feature is disabled)
<Route path="widgets" element={<RsuRouteGuard><WidgetsPage /></RsuRouteGuard>} />

// Inline conditional render (hides children when feature is disabled)
<ConditionalRenderIntersection>
  <IntersectionWidget />
</ConditionalRenderIntersection>
```
Available guards/renders: `RsuRouteGuard`, `IntersectionRouteGuard`, `WzdxRouteGuard`, `HaasRouteGuard`, `ConditionalRenderRsu`, `ConditionalRenderIntersection`, `ConditionalRenderWzdx`, `ConditionalRenderHaas` — all from `src/feature-flags.tsx`.

### Reusable components
Before building a one-off UI element, check `src/components/` and `src/styles/components/` for existing shared components (`AdminButton`, `SideBarHeader`, `ErrorMessageText` …). Create new shared components there when a pattern appears more than once.

---

## 5. Forms — Formik + Yup

Use `useFormik` with a Yup `validationSchema`. Connect to MUI fields via `formik.getFieldProps`.

```tsx
import * as Yup from 'yup'
import { useFormik } from 'formik'
import { TextField, Button, Grid2 } from '@mui/material'
import toast from 'react-hot-toast'

interface WidgetFormValues {
  name: string
  organizationId: string
}

const WidgetForm = ({ onSuccess }: { onSuccess: () => void }) => {
  const [createWidget] = useCreateWidgetMutation()

  const formik = useFormik<WidgetFormValues>({
    initialValues: { name: '', organizationId: '' },
    validationSchema: Yup.object({
      name: Yup.string().required('Name is required').max(100),
      organizationId: Yup.string().required('Organization is required'),
    }),
    onSubmit: async (values, helpers) => {
      try {
        await createWidget(values).unwrap()
        toast.success('Widget created')
        helpers.resetForm()
        onSuccess()
      } catch {
        toast.error('Failed to create widget')
        helpers.setStatus({ success: false })
      } finally {
        helpers.setSubmitting(false)
      }
    },
  })

  return (
    <form onSubmit={formik.handleSubmit}>
      <Grid2 container spacing={2}>
        <Grid2 size={12}>
          <TextField
            fullWidth
            label="Name"
            {...formik.getFieldProps('name')}
            error={formik.touched.name && Boolean(formik.errors.name)}
            helperText={formik.touched.name && formik.errors.name}
          />
        </Grid2>
        <Grid2 size={12}>
          <Button type="submit" variant="contained" disabled={formik.isSubmitting}>
            Create Widget
          </Button>
        </Grid2>
      </Grid2>
    </form>
  )
}
```

---

## 6. Testing

### Component tests (`*.component.test.tsx`)
- Snapshot with all redux state at default values
- Mock fetch calls with `jest-fetch-mock` / `fetchMock`
- Wrap with real or mock redux `Provider` using `setupStore()`

### Slice tests (`*Slice.test.ts`)
- Verify initial state
- Test each reducer, async thunk (pending / fulfilled / rejected), and selector

### RTK Query slice tests (`*ApiSlice.test.ts`)
```ts
import fetchMock from 'jest-fetch-mock'
import { setupStore } from '../../store'
import { widgetApiSlice } from './widgetApiSlice'
import EnvironmentVars from '../../EnvironmentVars'

const BASE_URL = `${EnvironmentVars.CVIZ_API_SERVER_URL}/widgets`
const mockUserState = {
  user: {
    value: {
      authLoginData: { token: 'test-token' },
      organization: { organization: 'test-org', role: 'admin' },
    },
  },
}

beforeEach(() => { fetchMock.resetMocks(); fetchMock.doMock() })

it('fetches widgets', async () => {
  fetchMock.mockResponseOnce(JSON.stringify([{ id: '1', name: 'W1' }]))
  const store = setupStore({ ...mockUserState } as any)
  const result = await store.dispatch(
    widgetApiSlice.endpoints.getWidgets.initiate({ organization: 'test-org' })
  )
  expect(result.data).toHaveLength(1)
})
```

---

## Workflow — Adding a New Feature

1. **Types**: add/extend `.d.ts` files in `src/models/`
2. **RTK API slice**: create `src/features/api/<name>ApiSlice.ts` following §2
3. **Redux slice** (if shared state needed): create `src/features/<name>/<name>Slice.ts` following §3; register in `store.ts`
4. **Components**: build page + sub-components; prop interfaces at top of each file
5. **Forms**: use Formik + Yup per §5
6. **Routing**: add route in router config; wrap in feature-flag guard if applicable
7. **Tests**: component snapshot + RTK slice tests
8. **Reusability check**: extract any pattern used ≥2 times into `src/components/`
