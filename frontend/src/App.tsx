import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { AppLayout } from './layout/AppLayout';
import { ProtectedRoute } from './routes/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { CataloguePage } from './pages/CataloguePage';
import { ServiceRequestTypesPage } from './pages/ServiceRequestTypesPage';
import { NewRequestPage } from './pages/NewRequestPage';
import { MyRequestsPage } from './pages/MyRequestsPage';
import { RequestDetailPage } from './pages/RequestDetailPage';
import { TasksPage } from './pages/TasksPage';
import { DashboardPage } from './pages/DashboardPage';
import { NotificationsPage } from './pages/NotificationsPage';

/** §9.4 - écrans principaux. La protection réelle des données reste côté serveur
 * (RG-06, canAct) : ce routeur ne fait que masquer les écrans à un visiteur non connecté,
 * ce que §11.1 appelle explicitement insuffisant à lui seul. */
export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route index element={<Navigate to="/catalogue" replace />} />
            <Route path="/catalogue" element={<CataloguePage />} />
            <Route path="/catalogue/:serviceId" element={<ServiceRequestTypesPage />} />
            <Route path="/mes-demandes" element={<MyRequestsPage />} />
            <Route path="/demandes/nouvelle/:requestTypeId" element={<NewRequestPage />} />
            <Route path="/demandes/:id" element={<RequestDetailPage />} />
            <Route path="/mes-taches" element={<TasksPage />} />
            <Route path="/tableau-de-bord" element={<DashboardPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/catalogue" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
